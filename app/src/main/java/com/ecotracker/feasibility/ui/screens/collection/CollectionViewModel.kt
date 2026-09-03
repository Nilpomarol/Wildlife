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
import com.wildlife.feasibility.ExtraDiscoveryProjection
import com.wildlife.feasibility.ObservationStore
import com.wildlife.feasibility.OffCatalogueTaxonEnricher
import com.wildlife.feasibility.RemoteTaxonStore
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
            if (!loadGate.isLatest(revision)) return@launch
            uiState = loaded.copy(isLoading = false)
            val missingExtraTaxa = loaded.entries.mapNotNull { entry ->
                entry.taxonId?.takeIf {
                    entry.extraDiscoveryContexts.isNotEmpty() && entry.scientificName == null
                }
            }
            val improved = withContext(Dispatchers.IO) {
                OffCatalogueTaxonEnricher.enrich(getApplication(), missingExtraTaxa)
            }
            if (improved && loadGate.isLatest(revision)) {
                uiState = withContext(Dispatchers.IO) { load() }.copy(isLoading = false)
            }
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
        // Collection is personal history. The regional guide, including missing species,
        // belongs to Explore.
        val account = AccountStore(context).verified()
        return runCatching {
            val content = PublishedContentRepositories.application(context)
            val catalogues = content.catalogues()
            val regionStore = RegionContextStore(context)
            val regionContext = regionStore.currentRegion()
            val selectedKey = regionStore.currentRegionKey(
                catalogues.mapTo(mutableSetOf()) { it.regionKey },
            )
            val selected = catalogues.firstOrNull { it.regionKey == selectedKey }
            val (observations, summary, observationRegionsByUuid) = if (account == null) {
                Triple(emptyList<SyncedObservation>(), null, emptyMap())
            } else {
                ObservationStore(context).use { store ->
                    val all = store.observations(account.userId)
                    Triple(
                        all,
                        store.summary(account.userId),
                        all.mapNotNull { observation ->
                            store.observationRegion(account.userId, observation.uuid)
                                ?.let { observation.uuid to it }
                        }.toMap(),
                    )
                }
            }
            val catalogueTaxaByRegion = catalogues.associate { catalogue ->
                catalogue.regionKey to content.taxa(catalogue.regionKey)
            }
            val catalogueTaxonIdsByRegion = catalogueTaxaByRegion.mapValues { (_, taxa) ->
                taxa.mapTo(mutableSetOf()) { it.taxonId }
            }
            val extraContextsByTaxon = ExtraDiscoveryProjection.contextsByTaxon(
                observations = observations,
                regionsByObservationUuid = observationRegionsByUuid,
                catalogues = catalogues,
                catalogueTaxaByRegion = catalogueTaxonIdsByRegion,
            )
            val catalogueTaxaById = catalogues
                .flatMap { catalogue -> catalogueTaxaByRegion[catalogue.regionKey].orEmpty() }
                .associateBy { it.taxonId }
            val publishedByTaxon = catalogues
                .flatMap { catalogue -> content.taxaContent(catalogue.regionKey).values }
                .associateBy { it.taxonId }
            val remoteByTaxon = RemoteTaxonStore(context).use { remote ->
                extraContextsByTaxon.keys.mapNotNull { taxonId ->
                    remote.cached(taxonId)?.let { taxonId to it }
                }.toMap()
            }
            val mediaStore = LocalMediaStore(context)
            val entries = CollectionProjection.species(observations).map { collected ->
                val taxon = collected.taxonId?.let(catalogueTaxaById::get)
                val published = collected.taxonId?.let(publishedByTaxon::get)
                val details = published?.toLocalTaxonDetails(mediaStore)
                    ?: collected.taxonId?.let(remoteByTaxon::get)
                collected.copy(
                    label = taxon?.commonName ?: details?.commonName ?: collected.label,
                    silhouetteUrl = details?.silhouetteLocalUri,
                    silhouetteFallbackUrl = details?.silhouetteFallbackUrl ?: details?.silhouetteUrl,
                    silhouetteMatchRank = details?.silhouetteMatchRank,
                    // Rarity and standing are regional properties and therefore stay on
                    // Explore's selected guide rather than this all-regions projection.
                    regionalEssential = false,
                    regionalIcon = false,
                    scientificName = taxon?.scientificName ?: details?.scientificName,
                    encounterRarity = null,
                    taxonGroup = taxon?.let { taxonGroupFor(it.taxonClass) }
                        ?: taxonGroupForClass(details?.taxonGroup),
                    extraDiscoveryContexts = collected.taxonId
                        ?.let(extraContextsByTaxon::get)
                        .orEmpty(),
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
                achievements = selectedKey?.let(content::achievements).orEmpty(),
                observedRegionalTaxa = emptySet(),
                // Read-only projection: the shell owns recording the highest level reached.
                progression = account?.let {
                    val store = ProgressionStore(context)
                    ProgressionProjection.project(
                        totalXp = summary?.totalXp ?: 0,
                        selectedLevelKey = store.selectedLevelKey(it.userId),
                        highestLevelKey = store.highestLevelKey(it.userId),
                    )
                },
                mediaPrefetch = selectedKey?.let { key ->
                    MediaPrefetchStore(context).use {
                        it.summary(content.generation().generationId, key)
                    }
                } ?: MediaPrefetchSummary(0, 0, 0, 0),
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
