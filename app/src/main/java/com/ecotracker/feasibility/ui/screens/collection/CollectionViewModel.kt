package com.wildlife.feasibility.ui.screens.collection

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wildlife.feasibility.AccountStore
import com.wildlife.feasibility.CollectionProjection
import com.wildlife.feasibility.CollectionSpecies
import com.wildlife.feasibility.ObservationStore
import com.wildlife.feasibility.ActiveCatalogueStore
import com.wildlife.feasibility.InstalledRegionalCatalogue
import com.wildlife.feasibility.RegionalCatalogueAssetStore
import com.wildlife.feasibility.InstalledRegionalAchievement
import com.wildlife.feasibility.CatalogueStore
import com.wildlife.feasibility.OnDeviceWildlifeRepository
import com.wildlife.feasibility.RegionalSilhouetteCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Maps a Linnaean class from the frozen catalogue to a normalized, filterable group key. */
internal fun taxonGroupFor(taxonClass: String): String? = when (taxonClass) {
    "Mammalia" -> "mammals"
    "Aves" -> "birds"
    "Reptilia" -> "reptiles"
    "Amphibia" -> "amphibians"
    "Actinopterygii", "Chondrichthyes", "Myxini", "Petromyzonti" -> "fish"
    else -> null
}

data class CollectionUiState(
    val linked: Boolean,
    val entries: List<CollectionSpecies>,
    val observationCount: Int,
    val totalXp: Int,
    val lastSyncedAtMs: Long? = null,
    val errorMessage: String? = null,
    val selectedCatalogue: InstalledRegionalCatalogue? = null,
    val installedCatalogues: List<InstalledRegionalCatalogue> = emptyList(),
    val achievements: List<InstalledRegionalAchievement> = emptyList(),
    val observedRegionalTaxa: Set<Long> = emptySet(),
) {
    val awaitingIdentificationCount: Int
        get() = entries.count(CollectionSpecies::awaitingSpeciesIdentification)

    val identifiedSpeciesCount: Int
        get() = entries.size - awaitingIdentificationCount
}

class CollectionViewModel(application: Application) : AndroidViewModel(application) {
    var uiState by mutableStateOf(load())
        private set
    private var silhouetteEnrichmentInFlight = false
    private var silhouetteBatchesRemaining = SILHOUETTE_BATCHES_PER_SESSION

    init {
        enrichSilhouettesIfNeeded()
    }

    fun refresh() {
        uiState = load()
        enrichSilhouettesIfNeeded()
    }

    fun selectRegion(regionKey: String) {
        ActiveCatalogueStore(getApplication()).selectRegion(regionKey)
        refresh()
    }

    private fun load(): CollectionUiState {
        val context = getApplication<Application>()
        val account = AccountStore(context).verified()
            ?: return CollectionUiState(
                linked = false,
                entries = emptyList(),
                observationCount = 0,
                totalXp = 0,
            )
        return runCatching {
            val content = RegionalCatalogueAssetStore(context)
            val catalogues = content.catalogues()
            val selectedKey = ActiveCatalogueStore(context).selectedRegionKey()
                .takeIf { selected -> catalogues.any { it.regionKey == selected } }
                ?: catalogues.first().regionKey
            val selected = catalogues.first { it.regionKey == selectedKey }
            val (observations, summary) = ObservationStore(context).use { store ->
                val all = store.observations(account.userId)
                val regionalObservations = all.filter { observation ->
                    store.observationRegion(account.userId, observation.uuid)?.let { assignment ->
                        assignment.earnsRegionalProgress && assignment.regionKey == selectedKey
                    } == true
                }
                regionalObservations to store.summary(account.userId)
            }
            val observationsByTaxon = observations.groupBy { it.collectionTaxonId ?: it.taxonId }
            val achievementTypesByTaxon = content.achievements(selectedKey)
                .flatMap { achievement -> achievement.taxonIds.map { it to achievement.label } }
                .groupBy({ it.first }, { it.second })
            val silhouetteDetails = CatalogueStore(context).use { it.loadTaxonDetails() }
            val entries = content.taxa(selectedKey).map { taxon ->
                val sightings = observationsByTaxon[taxon.taxonId].orEmpty()
                val latest = sightings.maxByOrNull { it.observedAtMs }
                val silhouette = silhouetteDetails[taxon.taxonId]
                val silhouetteUrl = silhouette?.silhouetteLocalUri ?: silhouette?.silhouetteUrl
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
                    silhouetteFallbackUrl = silhouette?.silhouetteUrl?.takeIf { it != silhouetteUrl },
                    silhouetteMatchRank = silhouette?.silhouetteMatchRank,
                    regionalEssential = "essentials" in achievementTypesByTaxon[taxon.taxonId].orEmpty(),
                    regionalIcon = "icons" in achievementTypesByTaxon[taxon.taxonId].orEmpty(),
                    scientificName = taxon.scientificName,
                    encounterRarity = taxon.rarity,
                    regionalPrestige = taxon.prestige,
                    taxonGroup = taxonGroupFor(taxon.taxonClass),
                )
            }
            CollectionUiState(
                linked = true,
                entries = entries,
                observationCount = observations.size,
                totalXp = summary.totalXp,
                lastSyncedAtMs = summary.lastSyncedAtMs,
                selectedCatalogue = selected,
                installedCatalogues = catalogues,
                achievements = content.achievements(selectedKey),
                observedRegionalTaxa = observations.mapNotNull { it.collectionTaxonId ?: it.taxonId }.toSet(),
            )
        }.getOrElse { error ->
            CollectionUiState(
                linked = true,
                entries = emptyList(),
                observationCount = 0,
                totalXp = 0,
                errorMessage = error.message ?: "The local collection could not be read.",
            )
        }
    }

    private fun enrichSilhouettesIfNeeded() {
        if (silhouetteEnrichmentInFlight || !uiState.linked || silhouetteBatchesRemaining <= 0) return
        val candidates = uiState.entries.mapNotNull { entry ->
            entry.taxonId?.let { taxonId ->
                entry.scientificName?.let { name ->
                    RegionalSilhouetteCandidate(taxonId, name, entry.taxonGroup)
                }
            }
        }
        if (candidates.isEmpty()) return
        silhouetteEnrichmentInFlight = true
        viewModelScope.launch {
            val changed = withContext(Dispatchers.IO) {
                OnDeviceWildlifeRepository(getApplication()).use {
                    it.enrichRegionalSilhouettes(candidates)
                }
            }
            silhouetteEnrichmentInFlight = false
            if (changed) {
                uiState = load()
                silhouetteBatchesRemaining -= 1
                if (silhouetteBatchesRemaining > 0) enrichSilhouettesIfNeeded()
            }
        }
    }

    private companion object {
        /** Keeps the first collection visit responsive while progressively improving visible cards. */
        const val SILHOUETTE_BATCHES_PER_SESSION = 3
    }
}
