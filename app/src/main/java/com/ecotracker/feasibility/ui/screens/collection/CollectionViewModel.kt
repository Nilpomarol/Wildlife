package com.wildlife.feasibility.ui.screens.collection

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.wildlife.feasibility.AccountStore
import com.wildlife.feasibility.CollectionProjection
import com.wildlife.feasibility.CollectionSpecies
import com.wildlife.feasibility.ObservationStore
import com.wildlife.feasibility.SyncedObservation
import com.wildlife.feasibility.RegionContextStore
import com.wildlife.feasibility.CurrentRegionSource
import com.wildlife.feasibility.InstalledRegionalCatalogue
import com.wildlife.feasibility.PublishedContentRepositories
import com.wildlife.feasibility.InstalledRegionalAchievement
import com.wildlife.feasibility.ProgressionProjection
import com.wildlife.feasibility.ProgressionState
import com.wildlife.feasibility.ProgressionStore
import com.wildlife.feasibility.taxonGroupForClass
import com.wildlife.feasibility.toLocalTaxonDetails
import com.wildlife.feasibility.LocalMediaStore
import com.wildlife.feasibility.MediaPrefetchStore
import com.wildlife.feasibility.MediaPrefetchSummary
import com.wildlife.feasibility.MediaPrefetchScheduler
import com.wildlife.feasibility.ui.ProjectionLoadGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Maps a Linnaean class from the frozen catalogue to a normalized, filterable group key.
 *
 * Delegates to [taxonGroupForClass]: the API client needs the same mapping and cannot call
 * into the UI layer, so the table itself lives beside the models.
 */
internal fun taxonGroupFor(taxonClass: String): String? = taxonGroupForClass(taxonClass)

data class CollectionUiState(
    val linked: Boolean,
    val entries: List<CollectionSpecies>,
    val observationCount: Int,
    val totalXp: Int,
    val isLoading: Boolean = false,
    val lastSyncedAtMs: Long? = null,
    val errorMessage: String? = null,
    val selectedCatalogue: InstalledRegionalCatalogue? = null,
    val installedCatalogues: List<InstalledRegionalCatalogue> = emptyList(),
    val achievements: List<InstalledRegionalAchievement> = emptyList(),
    val observedRegionalTaxa: Set<Long> = emptySet(),
    /**
     * The account's real XP progression. The header shows this rather than a
     * collection-local rank ladder, so one vocabulary of titles exists app-wide.
     */
    val progression: ProgressionState? = null,
    val mediaPrefetch: MediaPrefetchSummary = MediaPrefetchSummary(0, 0, 0, 0),
    val currentRegionSource: CurrentRegionSource = CurrentRegionSource.UNAVAILABLE,
) {
    val awaitingIdentificationCount: Int
        get() = entries.count(CollectionSpecies::awaitingSpeciesIdentification)

    val identifiedSpeciesCount: Int
        get() = entries.size - awaitingIdentificationCount
}

class CollectionViewModel(application: Application) : AndroidViewModel(application) {
    private val mediaWork = MediaPrefetchScheduler.workInfos(application)
    private val mediaObserver = Observer<List<WorkInfo>> { refreshMediaProgress() }
    private var mediaSummaryJob: Job? = null
    private var loadJob: Job? = null
    private val loadGate = ProjectionLoadGate()
    var uiState by mutableStateOf(
        CollectionUiState(
            linked = false,
            entries = emptyList(),
            observationCount = 0,
            totalXp = 0,
            isLoading = true,
        ),
    )
        private set
    init {
        mediaWork.observeForever(mediaObserver)
        refresh()
    }

    fun refresh() {
        val revision = loadGate.next()
        loadJob?.cancel()
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        loadJob = viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { load() }
            if (loadGate.isLatest(revision)) uiState = loaded.copy(isLoading = false)
        }
    }

    private fun refreshMediaProgress() {
        val regionKey = uiState.selectedCatalogue?.regionKey ?: return
        mediaSummaryJob?.cancel()
        mediaSummaryJob = viewModelScope.launch {
            val summary = runCatching {
                withContext(Dispatchers.IO) {
                    val context = getApplication<Application>()
                    val generation = PublishedContentRepositories.application(context)
                        .generation().generationId
                    MediaPrefetchStore(context).use { it.summary(generation, regionKey) }
                }
            }.getOrNull() ?: return@launch
            if (uiState.selectedCatalogue?.regionKey == regionKey &&
                uiState.mediaPrefetch != summary
            ) {
                uiState = uiState.copy(mediaPrefetch = summary)
            }
        }
    }

    private fun load(): CollectionUiState {
        val context = getApplication<Application>()
        // Unlinked users still see the regional guide as silhouettes; only the personal
        // observation layer is missing, so the screen no longer replaces itself with a wall.
        val account = AccountStore(context).verified()
        return runCatching {
            val content = PublishedContentRepositories.application(context)
            val catalogues = content.catalogues()
            val regionStore = RegionContextStore(context)
            val regionContext = regionStore.currentRegion()
            val selectedKey = regionStore.currentRegionKey(
                catalogues.mapTo(mutableSetOf()) { it.regionKey },
            ) ?: return@runCatching CollectionUiState(
                linked = account != null,
                entries = emptyList(),
                observationCount = 0,
                totalXp = 0,
                installedCatalogues = catalogues,
                currentRegionSource = regionContext.source,
                errorMessage = "Current region unavailable. Allow location from Check near me, or browse another guide in Explore.",
            )
            val selected = catalogues.first { it.regionKey == selectedKey }
            val (observations, summary) = if (account == null) {
                emptyList<SyncedObservation>() to null
            } else {
                ObservationStore(context).use { store ->
                    val all = store.observations(account.userId)
                    val regionalObservations = all.filter { observation ->
                        store.observationRegion(account.userId, observation.uuid)?.let { assignment ->
                            assignment.earnsRegionalProgress && assignment.regionKey == selectedKey
                        } == true
                    }
                    regionalObservations to store.summary(account.userId)
                }
            }
            val observationsByTaxon = observations.groupBy { it.collectionTaxonId ?: it.taxonId }
            val achievementTypesByTaxon = content.achievements(selectedKey)
                .flatMap { achievement -> achievement.taxonIds.map { it to achievement.label } }
                .groupBy({ it.first }, { it.second })
            val publishedByTaxon = content.taxaContent(selectedKey)
            val mediaStore = LocalMediaStore(context)
            val entries = content.taxa(selectedKey).map { taxon ->
                val sightings = observationsByTaxon[taxon.taxonId].orEmpty()
                val latest = sightings.maxByOrNull { it.observedAtMs }
                val published = publishedByTaxon[taxon.taxonId]
                val details = published?.toLocalTaxonDetails(mediaStore)
                val silhouetteUrl = details?.silhouetteLocalUri
                CollectionSpecies(
                    key = "taxon:${taxon.taxonId}", taxonId = taxon.taxonId,
                    label = taxon.commonName, observationCount = sightings.size,
                    rewardedObservationCount = sightings.count { it.confirmed },
                    latestObservationUuid = latest?.uuid ?: "regional:${taxon.taxonId}",
                    latestObservedAtMs = latest?.observedAtMs ?: 0,
                    bestQualityGrade = sightings.maxByOrNull { if (it.qualityGrade == "research") 2 else 1 }
                        ?.qualityGrade ?: "",
                    awaitingSpeciesIdentification = false,
                    photoUrl = sightings.firstNotNullOfOrNull { it.photoUrl },
                    silhouetteUrl = silhouetteUrl,
                    silhouetteFallbackUrl = details?.silhouetteFallbackUrl,
                    silhouetteMatchRank = details?.silhouetteMatchRank,
                    regionalEssential = "essentials" in achievementTypesByTaxon[taxon.taxonId].orEmpty(),
                    regionalIcon = "icons" in achievementTypesByTaxon[taxon.taxonId].orEmpty(),
                    scientificName = taxon.scientificName,
                    encounterRarity = taxon.rarity,
                    taxonGroup = taxonGroupFor(taxon.taxonClass),
                )
            }
            CollectionUiState(
                linked = account != null,
                entries = entries,
                observationCount = observations.size,
                totalXp = summary?.totalXp ?: 0,
                lastSyncedAtMs = summary?.lastSyncedAtMs,
                selectedCatalogue = selected,
                installedCatalogues = catalogues,
                achievements = content.achievements(selectedKey),
                observedRegionalTaxa = observations.mapNotNull { it.collectionTaxonId ?: it.taxonId }.toSet(),
                // Read-only projection: the shell owns recording the highest level reached.
                progression = account?.let {
                    val store = ProgressionStore(context)
                    ProgressionProjection.project(
                        totalXp = summary?.totalXp ?: 0,
                        selectedLevelKey = store.selectedLevelKey(it.userId),
                        highestLevelKey = store.highestLevelKey(it.userId),
                    )
                },
                mediaPrefetch = MediaPrefetchStore(context).use {
                    it.summary(content.generation().generationId, selectedKey)
                },
                currentRegionSource = regionContext.source,
            )
        }.getOrElse { error ->
            CollectionUiState(
                linked = account != null,
                entries = emptyList(),
                observationCount = 0,
                totalXp = 0,
                errorMessage = error.message ?: "The local collection could not be read.",
            )
        }
    }

    override fun onCleared() {
        mediaWork.removeObserver(mediaObserver)
        super.onCleared()
    }

}
