package com.wildlife.feasibility.ui.screens.explore

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wildlife.feasibility.AccountStore
import com.wildlife.feasibility.CatalogueSnapshot
import com.wildlife.feasibility.CataloguePhotoPolicy
import com.wildlife.feasibility.CatalogueStore
import com.wildlife.feasibility.CollectionProjection
import com.wildlife.feasibility.NearbySpecies
import com.wildlife.feasibility.ObservationStore
import com.wildlife.feasibility.OnDeviceWildlifeRepository
import com.wildlife.feasibility.SyncedObservation
import com.wildlife.feasibility.TaxonDetails
import com.wildlife.feasibility.ui.components.SpeciesCardModel
import com.wildlife.feasibility.ui.components.SpeciesCardPhotoKind
import com.wildlife.feasibility.ui.components.SpeciesCardPhotoCandidate
import com.wildlife.feasibility.ui.components.SpeciesCardStatus
import com.wildlife.feasibility.ui.screens.map.PersonalObservationMap
import com.wildlife.feasibility.ui.screens.map.PersonalObservationMapProjection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ExploreSpecies(
    val taxonId: Long,
    val taxonGroup: String?,
    val commonName: String?,
    val scientificName: String,
    val observed: Boolean,
    val card: SpeciesCardModel,
)

data class ExploreUiState(
    val snapshot: CatalogueSnapshot? = null,
    val entries: List<ExploreSpecies> = emptyList(),
    val accountLinked: Boolean = false,
    val personalMap: PersonalObservationMap = PersonalObservationMapProjection.build(emptyList()),
    val nearby: NearbyDiscoveryState = NearbyDiscoveryState(),
    val syncing: Boolean = false,
    val silhouetteEnrichmentRunning: Boolean = false,
    val errorMessage: String? = null,
) {
    val observedCount: Int get() = entries.count(ExploreSpecies::observed)
}

data class NearbyDiscoveryState(
    val requested: Boolean = false,
    val loading: Boolean = false,
    val species: List<NearbySpecies> = emptyList(),
    val errorMessage: String? = null,
    val radiusKm: Int = 25,
)

internal object ExploreProjection {
    fun entries(
        snapshot: CatalogueSnapshot?,
        observations: List<SyncedObservation>,
        detailsByTaxon: Map<Long, TaxonDetails> = emptyMap(),
    ): List<ExploreSpecies> {
        if (snapshot == null) return emptyList()
        val collectionByTaxon = CollectionProjection.species(observations)
            .mapNotNull { entry -> entry.taxonId?.let { it to entry } }
            .toMap()
        return snapshot.species.map { species ->
            val collected = collectionByTaxon[species.taxonId]
            val details = detailsByTaxon[species.taxonId]
            val detailPhoto = details?.let(CataloguePhotoPolicy::detailUrl)
            val detailAttribution = detailPhoto?.let {
                details?.let(CataloguePhotoPolicy::attribution)
            }
            val cataloguePhoto = CataloguePhotoPolicy.cardUrl(species)?.let {
                species.photoLocalUri ?: it
            }
            val referencePhoto = detailPhoto?.let { details?.photoLocalUri ?: it }
                ?: cataloguePhoto
            val referencePhotoRemote = detailPhoto ?: CataloguePhotoPolicy.cardUrl(species)
            val personalPhoto = collected?.photoUrl
            val displayedPhoto = if (collected != null) personalPhoto ?: referencePhoto else null
            val fallbackPhoto = when {
                displayedPhoto != null && displayedPhoto == personalPhoto -> referencePhoto
                displayedPhoto != null && displayedPhoto == referencePhoto -> referencePhotoRemote
                else -> null
            }?.takeIf { it != displayedPhoto }
            val displayedAttribution = if (displayedPhoto == referencePhoto) {
                detailAttribution ?: CataloguePhotoPolicy.attribution(species)
            } else {
                null
            }
            val label = species.commonName ?: species.scientificName
            val status = when {
                collected?.bestQualityGrade == "research" -> SpeciesCardStatus.RESEARCH_GRADE
                collected != null -> SpeciesCardStatus.OBSERVED
                else -> SpeciesCardStatus.NONE
            }
            val detailedSilhouette = details?.silhouetteUrl
            val silhouetteUrl = if (detailedSilhouette != null) {
                details.silhouetteLocalUri ?: detailedSilhouette
            } else {
                species.silhouetteLocalUri ?: species.silhouetteUrl
            }
            val silhouetteMatchRank = if (detailedSilhouette != null) {
                details?.silhouetteMatchRank
            } else {
                species.silhouetteMatchRank
            }
            ExploreSpecies(
                taxonId = species.taxonId,
                taxonGroup = species.taxonGroup,
                commonName = species.commonName,
                scientificName = species.scientificName,
                observed = collected != null,
                card = SpeciesCardModel(
                    key = "taxon:${species.taxonId}",
                    label = label,
                    supportingText = if (species.commonName != null) {
                        species.scientificName
                    } else {
                        "Scientific name"
                    },
                    photoUrl = displayedPhoto,
                    photoKind = when {
                        displayedPhoto == null -> null
                        displayedPhoto == personalPhoto -> SpeciesCardPhotoKind.PERSONAL
                        else -> SpeciesCardPhotoKind.REFERENCE
                    },
                    photoFallbackUrl = fallbackPhoto,
                    photoFallbackKind = fallbackPhoto?.let {
                        SpeciesCardPhotoKind.REFERENCE
                    },
                    photoFallbackAttribution = fallbackPhoto?.let {
                        detailAttribution ?: CataloguePhotoPolicy.attribution(species)
                    },
                    additionalPhotoFallbacks = buildList {
                        if (displayedPhoto != null && displayedPhoto == personalPhoto &&
                            referencePhotoRemote != null &&
                            referencePhotoRemote != displayedPhoto &&
                            referencePhotoRemote != referencePhoto
                        ) {
                            add(
                                SpeciesCardPhotoCandidate(
                                    referencePhotoRemote,
                                    SpeciesCardPhotoKind.REFERENCE,
                                    detailAttribution ?: CataloguePhotoPolicy.attribution(species),
                                ),
                            )
                        }
                    },
                    photoAttribution = displayedAttribution,
                    silhouetteUrl = silhouetteUrl,
                    silhouetteFallbackUrl = (details?.silhouetteUrl ?: species.silhouetteUrl)
                        ?.takeIf { it != silhouetteUrl },
                    silhouetteMatchRank = silhouetteMatchRank,
                    supportingTextItalic = true,
                    status = status,
                ),
            )
        }
    }
}

internal object NearbyDiscoveryProjection {
    fun withinCatalogue(
        nearby: List<NearbySpecies>,
        catalogue: List<ExploreSpecies>,
    ): List<NearbySpecies> {
        val catalogueTaxa = catalogue.mapTo(hashSetOf(), ExploreSpecies::taxonId)
        return nearby.filter { it.taxonId in catalogueTaxa }
    }
}

class ExploreViewModel(application: Application) : AndroidViewModel(application) {
    private var nearbyState = NearbyDiscoveryState()
    var uiState by mutableStateOf(loadLocal())
        private set
    private var silhouetteEnrichmentInFlight = false

    init {
        enrichSilhouettesIfNeeded()
    }

    fun refreshLocal() {
        if (!uiState.syncing) {
            uiState = loadLocal().copy(
                silhouetteEnrichmentRunning = silhouetteEnrichmentInFlight,
            )
            enrichSilhouettesIfNeeded()
        }
    }

    fun setObservationMapVisible(observationUuid: String, visible: Boolean) {
        val account = AccountStore(getApplication()).verified() ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                OnDeviceWildlifeRepository(getApplication()).use {
                    it.setObservationMapVisible(account, observationUuid, visible)
                }
            }
            uiState = loadLocal().copy(
                silhouetteEnrichmentRunning = silhouetteEnrichmentInFlight,
            )
        }
    }

    fun syncCatalogue() {
        if (uiState.syncing) return
        uiState = uiState.copy(syncing = true, errorMessage = null)
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    OnDeviceWildlifeRepository(getApplication()).use {
                        it.syncCataloniaCatalogue(force = true)
                    }
                }
            }
            uiState = result.fold(
                onSuccess = { loadLocal() },
                onFailure = { error ->
                    loadLocal().copy(
                        errorMessage = "Update failed. The stored catalogue is still available. " +
                            (error.message ?: "Try again later."),
                    )
                },
            )
            enrichSilhouettesIfNeeded()
        }
    }

    fun nearbyLocationStarted() {
        if (!uiState.nearby.loading) {
            nearbyState = uiState.nearby.copy(
                requested = true,
                loading = true,
                errorMessage = null,
            )
            uiState = uiState.copy(
                nearby = nearbyState,
            )
        }
    }

    fun nearbyLocationFailed(message: String) {
        nearbyState = uiState.nearby.copy(
            requested = true,
            loading = false,
            errorMessage = message,
        )
        uiState = uiState.copy(
            nearby = nearbyState,
        )
    }

    fun discoverNearby(latitude: Double, longitude: Double) {
        nearbyLocationStarted()
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    OnDeviceWildlifeRepository(getApplication()).use {
                        it.discoverNearbySpecies(
                            latitude = latitude,
                            longitude = longitude,
                            radiusKm = uiState.nearby.radiusKm,
                        )
                    }
                }
            }
            nearbyState = result.fold(
                    onSuccess = { species ->
                        uiState.nearby.copy(
                            requested = true,
                            loading = false,
                            species = NearbyDiscoveryProjection.withinCatalogue(
                                nearby = species,
                                catalogue = uiState.entries,
                            ),
                            errorMessage = null,
                        )
                    },
                    onFailure = { error ->
                        uiState.nearby.copy(
                            requested = true,
                            loading = false,
                            errorMessage = error.message
                                ?: "Nearby species could not be checked. Try again with coverage.",
                        )
                    },
                )
            uiState = uiState.copy(
                nearby = nearbyState,
            )
        }
    }

    private fun enrichSilhouettesIfNeeded() {
        val snapshot = uiState.snapshot ?: return
        if (
            snapshot.silhouettePipelineVersion ==
            OnDeviceWildlifeRepository.SILHOUETTE_PIPELINE_VERSION
        ) return
        if (silhouetteEnrichmentInFlight) return
        silhouetteEnrichmentInFlight = true
        uiState = uiState.copy(silhouetteEnrichmentRunning = true)
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    OnDeviceWildlifeRepository(getApplication()).use {
                        it.enrichCatalogueSilhouettes()
                    }
                }
            }
            val completed = result.getOrNull()?.silhouettePipelineVersion ==
                OnDeviceWildlifeRepository.SILHOUETTE_PIPELINE_VERSION
            uiState = if (completed) {
                loadLocal()
            } else {
                loadLocal().copy(
                    errorMessage = "Some detailed silhouettes could not be stored. " +
                        "The catalogue remains available with broader silhouettes.",
                )
            }
            silhouetteEnrichmentInFlight = false
        }
    }

    private fun loadLocal(): ExploreUiState = runCatching {
        val context = getApplication<Application>()
        val (snapshot, detailsByTaxon) = CatalogueStore(context).use { catalogueStore ->
            catalogueStore.load() to catalogueStore.loadTaxonDetails()
        }
        val account = AccountStore(context).verified()
        val observationStore = account?.let { ObservationStore(context) }
        val observations = account?.let { observationStore?.observations(it.userId) }.orEmpty()
        val hiddenObservationUuids = account?.let {
            observationStore?.mapHiddenObservationUuids(it.userId)
        }.orEmpty()
        observationStore?.close()
        ExploreUiState(
            snapshot = snapshot,
            entries = ExploreProjection.entries(snapshot, observations, detailsByTaxon),
            accountLinked = account != null,
            personalMap = PersonalObservationMapProjection.build(
                observations = observations,
                hiddenObservationUuids = hiddenObservationUuids,
            ),
            nearby = nearbyState,
            errorMessage = when {
                snapshot?.refreshInProgress == true ->
                    "The previous catalogue refresh was interrupted. Your stored guide is intact."
                snapshot?.lastRefreshErrorCode != null ->
                    "The last catalogue refresh did not finish. Your stored guide is intact."
                else -> null
            },
        )
    }.getOrElse { error ->
        ExploreUiState(
            nearby = nearbyState,
            errorMessage = error.message ?: "The offline catalogue could not be read.",
        )
    }
}
