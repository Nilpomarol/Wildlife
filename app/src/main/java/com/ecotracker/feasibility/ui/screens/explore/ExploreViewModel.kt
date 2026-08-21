package com.wildlife.feasibility.ui.screens.explore

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wildlife.feasibility.AccountStore
import com.wildlife.feasibility.CataloguePhotoPolicy
import com.wildlife.feasibility.CatalogueStore
import com.wildlife.feasibility.CollectionProjection
import com.wildlife.feasibility.ActiveCatalogueStore
import com.wildlife.feasibility.InstalledRegionalCatalogue
import com.wildlife.feasibility.InstalledRegionalTaxon
import com.wildlife.feasibility.NearbySpecies
import com.wildlife.feasibility.ObservationStore
import com.wildlife.feasibility.OnDeviceWildlifeRepository
import com.wildlife.feasibility.RegionalCatalogueAssetStore
import com.wildlife.feasibility.SyncedObservation
import com.wildlife.feasibility.TaxonDetails
import com.wildlife.feasibility.ui.components.SpeciesCardModel
import com.wildlife.feasibility.ui.components.SpeciesCardPhotoKind
import com.wildlife.feasibility.ui.components.SpeciesCardPhotoCandidate
import com.wildlife.feasibility.ui.components.SpeciesCardStatus
import com.wildlife.feasibility.ui.screens.collection.taxonGroupFor
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

/** The frozen regional content pack currently used by Explore. */
data class RegionalExploreCatalogue(
    val regionKey: String,
    val displayName: String,
    val version: String,
    val speciesCount: Int,
)

data class ExploreUiState(
    val activeCatalogue: RegionalExploreCatalogue? = null,
    val entries: List<ExploreSpecies> = emptyList(),
    val accountLinked: Boolean = false,
    val personalMap: PersonalObservationMap = PersonalObservationMapProjection.build(emptyList()),
    val nearby: NearbyDiscoveryState = NearbyDiscoveryState(),
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
    /** Kept for the legacy snapshot tests and detail-cache migration. */
    fun entries(
        snapshot: com.wildlife.feasibility.CatalogueSnapshot?,
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
                    fallbackSilhouetteGroup = species.taxonGroup,
                    supportingTextItalic = true,
                    status = status,
                ),
            )
        }
    }

    fun regionalEntries(
        taxa: List<InstalledRegionalTaxon>,
        observations: List<SyncedObservation>,
        detailsByTaxon: Map<Long, TaxonDetails> = emptyMap(),
    ): List<ExploreSpecies> {
        val collectionByTaxon = CollectionProjection.species(observations)
            .mapNotNull { entry -> entry.taxonId?.let { it to entry } }
            .toMap()
        return taxa.map { taxon ->
            val collected = collectionByTaxon[taxon.taxonId]
            val details = detailsByTaxon[taxon.taxonId]
            val detailPhoto = details?.let(CataloguePhotoPolicy::detailUrl)
            val personalPhoto = collected?.photoUrl
            val referencePhoto = detailPhoto?.let { details?.photoLocalUri ?: it }
            val displayedPhoto = if (collected != null) personalPhoto ?: referencePhoto else null
            val status = when {
                collected?.bestQualityGrade == "research" -> SpeciesCardStatus.RESEARCH_GRADE
                collected != null -> SpeciesCardStatus.OBSERVED
                else -> SpeciesCardStatus.NONE
            }
            val silhouetteUrl = details?.silhouetteLocalUri ?: details?.silhouetteUrl
            ExploreSpecies(
                taxonId = taxon.taxonId,
                taxonGroup = taxonGroupFor(taxon.taxonClass),
                commonName = taxon.commonName,
                scientificName = taxon.scientificName,
                observed = collected != null,
                card = SpeciesCardModel(
                    key = "taxon:${taxon.taxonId}",
                    label = taxon.commonName,
                    supportingText = taxon.scientificName,
                    photoUrl = displayedPhoto,
                    photoKind = when {
                        displayedPhoto == null -> null
                        displayedPhoto == personalPhoto -> SpeciesCardPhotoKind.PERSONAL
                        else -> SpeciesCardPhotoKind.REFERENCE
                    },
                    photoAttribution = if (displayedPhoto == referencePhoto) {
                        details?.let(CataloguePhotoPolicy::attribution)
                    } else null,
                    silhouetteUrl = silhouetteUrl,
                    silhouetteFallbackUrl = details?.silhouetteUrl?.takeIf { it != silhouetteUrl },
                    silhouetteMatchRank = details?.silhouetteMatchRank,
                    fallbackSilhouetteGroup = taxonGroupFor(taxon.taxonClass),
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
    fun refreshLocal() {
        uiState = loadLocal()
    }

    fun setObservationMapVisible(observationUuid: String, visible: Boolean) {
        val account = AccountStore(getApplication()).verified() ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                OnDeviceWildlifeRepository(getApplication()).use {
                    it.setObservationMapVisible(account, observationUuid, visible)
                }
            }
            uiState = loadLocal()
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

    private fun loadLocal(): ExploreUiState = runCatching {
        val context = getApplication<Application>()
        val detailsByTaxon = CatalogueStore(context).use { it.loadTaxonDetails() }
        val content = RegionalCatalogueAssetStore(context)
        val catalogues = content.catalogues()
        val selectedKey = ActiveCatalogueStore(context).selectedRegionKey()
            .takeIf { key -> catalogues.any { it.regionKey == key } }
            ?: catalogues.first().regionKey
        val selected = catalogues.first { it.regionKey == selectedKey }
        val account = AccountStore(context).verified()
        val observationStore = account?.let { ObservationStore(context) }
        val observations = account?.let { observationStore?.observations(it.userId) }.orEmpty()
        val regionalObservations = account?.let { linkedAccount ->
            observations.filter { observation ->
                observationStore?.observationRegion(linkedAccount.userId, observation.uuid)?.let { assignment ->
                    assignment.earnsRegionalProgress && assignment.regionKey == selectedKey
                } == true
            }
        }.orEmpty()
        val hiddenObservationUuids = account?.let {
            observationStore?.mapHiddenObservationUuids(it.userId)
        }.orEmpty()
        observationStore?.close()
        ExploreUiState(
            activeCatalogue = RegionalExploreCatalogue(
                regionKey = selected.regionKey,
                displayName = selected.displayName,
                version = selected.version,
                speciesCount = content.taxa(selectedKey).size,
            ),
            entries = ExploreProjection.regionalEntries(
                taxa = content.taxa(selectedKey), observations = regionalObservations,
                detailsByTaxon = detailsByTaxon,
            ),
            accountLinked = account != null,
            personalMap = PersonalObservationMapProjection.build(
                observations = observations,
                hiddenObservationUuids = hiddenObservationUuids,
            ),
            nearby = nearbyState,
            errorMessage = null,
        )
    }.getOrElse { error ->
        ExploreUiState(
            nearby = nearbyState,
            errorMessage = error.message ?: "The offline catalogue could not be read.",
        )
    }
}
