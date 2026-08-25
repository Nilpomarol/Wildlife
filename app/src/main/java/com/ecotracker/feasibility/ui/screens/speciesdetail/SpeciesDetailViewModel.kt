package com.wildlife.feasibility.ui.screens.speciesdetail

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.wildlife.feasibility.AccountStore
import com.wildlife.feasibility.AlternativePhotoResult
import com.wildlife.feasibility.CataloguePhotoPolicy
import com.wildlife.feasibility.ObservationStore
import com.wildlife.feasibility.ObservationDensitySnapshot
import com.wildlife.feasibility.ObservationDensityStore
import com.wildlife.feasibility.OnDeviceWildlifeRepository
import com.wildlife.feasibility.SyncedObservation
import com.wildlife.feasibility.TaxonDetails
import com.wildlife.feasibility.RegionContextStore
import com.wildlife.feasibility.RegionalCatalogueAssetStore
import com.wildlife.feasibility.PublishedContentRepositories
import com.wildlife.feasibility.InstalledRegionalTaxon
import com.wildlife.feasibility.EncounterRarity
import com.wildlife.feasibility.toLocalTaxonDetails
import com.wildlife.feasibility.LocalMediaStore
import com.wildlife.feasibility.MediaPrefetchScheduler
import com.wildlife.feasibility.RemoteTaxonStore
import com.wildlife.feasibility.taxonGroupForClass
import com.wildlife.feasibility.mediaRepairNeeds
import com.wildlife.feasibility.ui.ProjectionLoadGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val TAXON_ID_KEY = "species_taxon_id"
const val TAXON_LABEL_KEY = "species_fallback_label"
const val REGION_KEY = "species_region_key"

data class SpeciesDetailObservation(
    val uuid: String,
    val observedAtMs: Long,
    val qualityGrade: String,
    val photoUrl: String?,
)

data class SpeciesRegionalContext(
    val regionName: String,
    val rarity: EncounterRarity,
    val achievementLabels: Set<String> = emptySet(),
)

data class SpeciesDetailUiState(
    val taxonId: Long,
    val commonName: String,
    val scientificName: String?,
    val taxonGroup: String?,
    val familyName: String?,
    val aboutSummary: String?,
    val wikipediaUrl: String?,
    val conservationStatus: String?,
    val conservationAuthority: String?,
    val conservationUrl: String?,
    val observed: Boolean,
    val researchGrade: Boolean,
    val bestQualityLabel: String,
    val heroPhotoUrl: String?,
    val heroFromCatalogue: Boolean,
    val heroAttribution: String?,
    val silhouetteUrl: String?,
    val silhouetteSourceUrl: String?,
    val silhouetteAttribution: String?,
    val silhouetteLicenseCode: String?,
    val silhouetteTaxonName: String?,
    val silhouetteMatchRank: String?,
    val catalogueVersion: String?,
    val observations: List<SpeciesDetailObservation>,
    val enriching: Boolean = false,
    val enrichmentMessage: String? = null,
    val errorMessage: String? = null,
    val referencePhotoSourceUrl: String? = null,
    val photoRecoveryStatus: String? = null,
    val mediaMessage: String? = null,
    val referencePhotoSourceLabel: String = "View image source on iNaturalist",
    val heroPhotoFallbackUrl: String? = null,
    val silhouetteFallbackUrl: String? = null,
    val silhouetteFallbackSourceUrl: String? = null,
    val silhouetteFallbackAttribution: String? = null,
    val silhouetteFallbackLicenseCode: String? = null,
    val silhouetteFallbackTaxonName: String? = null,
    val silhouetteFallbackMatchRank: String? = null,
    val mediaAttempt: Int = 0,
    val regionalContext: SpeciesRegionalContext? = null,
    val isLoading: Boolean = false,
    val fallbackSilhouetteGroup: String? = null,
    val mediaRetryAvailable: Boolean = false,
    val alternativeImageAvailable: Boolean = false,
    val observationDensity: ObservationDensitySnapshot? = null,
    val observationDensityLoading: Boolean = false,
    val observationDensityMessage: String? = null,
) {
    val latestObservedAtMs: Long? get() = observations.firstOrNull()?.observedAtMs
}

internal object SpeciesDetailProjection {
    fun build(
        taxonId: Long,
        fallbackLabel: String?,
        observations: List<SyncedObservation>,
        details: TaxonDetails? = null,
        regionalTaxon: InstalledRegionalTaxon? = null,
        regionalCatalogueVersion: String? = null,
    ): SpeciesDetailUiState {
        val personal = observations
            .filter { (it.collectionTaxonId ?: it.taxonId) == taxonId }
            .sortedByDescending(SyncedObservation::observedAtMs)
        val label = details?.commonName ?: regionalTaxon?.commonName
            ?: personal.firstNotNullOfOrNull { it.label.takeIf(String::isNotBlank) }
            ?: fallbackLabel?.takeIf(String::isNotBlank)
            ?: details?.scientificName ?: regionalTaxon?.scientificName
            ?: "Unknown species"
        val scientificName = (details?.scientificName ?: regionalTaxon?.scientificName)
            ?.takeUnless { it == label }
        val personalPhoto = personal.firstNotNullOfOrNull(SyncedObservation::photoUrl)
        val detailPhoto = details?.let(CataloguePhotoPolicy::detailUrl)?.let {
            details.photoLocalUri ?: it
        }
        val referencePhoto = detailPhoto
        val referencePhotoRemote = details?.let(CataloguePhotoPolicy::detailUrl)
        val silhouetteRemote = details?.silhouetteUrl
        val silhouette = details?.silhouetteUrl?.let { details.silhouetteLocalUri ?: it }
        val heroFromCatalogue = referencePhoto != null
        val bestQuality = personal.maxByOrNull { qualityRank(it.qualityGrade) }?.qualityGrade
        return SpeciesDetailUiState(
            taxonId = taxonId,
            commonName = label,
            scientificName = scientificName,
            taxonGroup = (details?.taxonGroup ?: regionalTaxon?.taxonClass)
                ?.let(::friendlyTaxonGroup),
            familyName = details?.familyName,
            aboutSummary = details?.wikipediaSummary,
            wikipediaUrl = details?.wikipediaUrl,
            conservationStatus = details?.conservationStatus,
            conservationAuthority = details?.conservationAuthority,
            conservationUrl = details?.conservationUrl,
            observed = personal.isNotEmpty(),
            researchGrade = personal.any { it.qualityGrade == "research" },
            bestQualityLabel = when (bestQuality) {
                "research" -> "Research grade"
                "needs_id" -> "Needs ID"
                "casual" -> "Casual"
                null -> "Not observed"
                else -> "Unverified"
            },
            heroPhotoUrl = referencePhoto ?: personalPhoto,
            heroPhotoFallbackUrl = referencePhotoRemote.takeIf {
                referencePhoto != null && referencePhoto != it
            },
            heroFromCatalogue = heroFromCatalogue,
            heroAttribution = details?.let(CataloguePhotoPolicy::attribution)
                .takeIf { heroFromCatalogue },
            silhouetteUrl = silhouette,
            silhouetteFallbackUrl = details?.silhouetteFallbackUrl
                ?: silhouetteRemote?.takeIf { it != silhouette },
            silhouetteFallbackSourceUrl = details?.silhouetteFallbackSourceUrl,
            silhouetteFallbackAttribution = details?.silhouetteFallbackAttribution,
            silhouetteFallbackLicenseCode = details?.silhouetteFallbackLicenseCode,
            silhouetteFallbackTaxonName = details?.silhouetteFallbackTaxonName,
            silhouetteFallbackMatchRank = details?.silhouetteFallbackMatchRank,
            silhouetteSourceUrl = details?.silhouetteSourceUrl,
            silhouetteAttribution = details?.silhouetteAttribution,
            silhouetteLicenseCode = details?.silhouetteLicenseCode,
            silhouetteTaxonName = details?.silhouetteTaxonName,
            silhouetteMatchRank = details?.silhouetteMatchRank,
            catalogueVersion = regionalCatalogueVersion,
            observations = personal.map {
                SpeciesDetailObservation(it.uuid, it.observedAtMs, it.qualityGrade, it.photoUrl)
            },
            referencePhotoSourceUrl = if (!heroFromCatalogue) null else {
                details?.photoSourceUrl ?: "https://www.inaturalist.org/taxa/$taxonId"
            },
            photoRecoveryStatus = details?.photoRecoveryStatus,
            referencePhotoSourceLabel = when (details?.photoRecoveryStatus) {
                "wikimedia_featured", "wikimedia_quality", "wikimedia_commons",
                OnDeviceWildlifeRepository.USER_REQUESTED_ALTERNATIVE ->
                    "View image on Wikimedia Commons"
                else -> "View image source on iNaturalist"
            },
            mediaMessage = when {
                personalPhoto != null || referencePhoto != null || details == null -> null
                details.photoRecoveryStatus == "recovery_failed" ->
                    "Reference photo unavailable."
                details.photoRecoveryStatus == "no_compatible_photo" ->
                    "No reusable reference photo is available."
                else -> "Reference photo unavailable."
            },
            fallbackSilhouetteGroup = taxonGroupForClass(
                details?.taxonGroup ?: regionalTaxon?.taxonClass,
            ),
            mediaRetryAvailable = details?.photoRecoveryStatus != null &&
                details.photoRecoveryStatus != "no_compatible_photo",
        )
    }

    private fun qualityRank(quality: String): Int = when (quality) {
        "research" -> 3
        "needs_id" -> 2
        "casual" -> 1
        else -> 0
    }

    private fun friendlyTaxonGroup(group: String): String = when (group) {
        "Mammalia" -> "Mammals"
        "Aves" -> "Birds"
        "Reptilia" -> "Reptiles"
        "Amphibia" -> "Amphibians"
        "Papilionoidea" -> "Butterflies"
        "Odonata" -> "Dragonflies and damselflies"
        "Actinopterygii" -> "Ray-finned fish"
        else -> group
    }
}

class SpeciesDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val taxonId = savedStateHandle.get<Long>(TAXON_ID_KEY) ?: -1L
    private val fallbackLabel = savedStateHandle.get<String>(TAXON_LABEL_KEY)
    private val requestedRegionKey = savedStateHandle.get<String>(REGION_KEY)
    private var mediaAttempt = 0
    private var loadJob: Job? = null
    private var mediaJob: Job? = null
    private var densityJob: Job? = null
    private var densitySnapshot: ObservationDensitySnapshot? = null
    private var densityMessage: String? = null
    private val loadGate = ProjectionLoadGate()
    private var publishedTaxon = false
    private var initialPreparationStarted = false

    var uiState by mutableStateOf(emptyState(isLoading = true))
        private set

    init { loadState() }

    fun refresh() {
        if (!uiState.enriching) loadState()
    }

    fun retryMedia() {
        if (uiState.enriching || mediaJob?.isActive == true) return
        mediaAttempt++
        if (publishedTaxon) downloadPublishedDetail() else enrich(force = true)
    }

    fun requestAlternativeImage() {
        if (!publishedTaxon || taxonId <= 0 || uiState.enriching || mediaJob?.isActive == true) return
        mediaAttempt++
        uiState = uiState.copy(enriching = true, enrichmentMessage = null)
        mediaJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                OnDeviceWildlifeRepository(getApplication()).use {
                    it.requestAlternativePublishedPhoto(taxonId)
                }
            }
            val message = when (result) {
                AlternativePhotoResult.Found ->
                    "A different reference image is now stored. The previous image will not be offered again."
                AlternativePhotoResult.NotAvailable ->
                    "No other suitable Wikimedia image was found. The rejected image will not be offered in future searches."
                AlternativePhotoResult.TemporaryFailure ->
                    "A different image could not be checked right now. The current image is unchanged."
            }
            applyProjection(message)
        }
    }

    fun retryObservationDensity() = loadObservationDensity(force = true)

    private fun loadState(enrichmentMessage: String? = null) {
        val revision = loadGate.next()
        val attempt = mediaAttempt
        loadJob?.cancel()
        uiState = uiState.copy(isLoading = true)
        loadJob = viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { load(attempt) }
            if (!loadGate.isLatest(revision)) return@launch
            publishedTaxon = loaded.published
            uiState = loaded.state.copy(
                isLoading = false,
                enrichmentMessage = enrichmentMessage,
            )
            if (!initialPreparationStarted) {
                initialPreparationStarted = true
                if (publishedTaxon) downloadPublishedDetail() else enrich()
            }
        }
    }

    private fun downloadPublishedDetail() {
        if (taxonId <= 0 || uiState.enriching || mediaJob?.isActive == true) return
        uiState = uiState.copy(enriching = true, enrichmentMessage = null)
        mediaJob = viewModelScope.launch {
            val context = getApplication<Application>()
            val content = PublishedContentRepositories.application(context)
            val published = withContext(Dispatchers.IO) { content.taxon(taxonId) }
                ?: return@launch applyProjection(null)
            val regionKey = withContext(Dispatchers.IO) { regionKeyForTaxon(content) }
            val photo = published.preferredPhoto()
            val photoFailure = if (photo != null && regionKey != null) {
                MediaPrefetchScheduler.enqueueDetail(context, regionKey, photo.assetId)
                withContext(Dispatchers.IO) {
                    LocalMediaStore(context).downloadNow(
                        content.generation().generationId,
                        photo,
                        "detail",
                    ).failureCode
                }
            } else null

            // Publish the hero result before any silhouette or discovery work starts.
            if (photo != null) {
                applyProjection(
                    "The reference image could not be stored yet."
                        .takeIf { photoFailure != null },
                )
            } else if (published.mediaRepairNeeds().photo) {
                val repaired = runCatching {
                    withContext(Dispatchers.IO) {
                        OnDeviceWildlifeRepository(context).use {
                            it.repairPublishedTaxonMedia(taxonId)
                        }
                    }
                }
                applyProjection(
                    "No suitable reusable reference image was found."
                        .takeIf { repaired.isSuccess && repaired.getOrNull()?.photoUrl == null }
                        ?: "The reference image could not be checked right now."
                            .takeIf { repaired.isFailure },
                )
            }

            // A specific silhouette is useful but never delays an available hero photograph.
            published.silhouetteHierarchy().forEach { silhouette ->
                if (regionKey != null) {
                    MediaPrefetchScheduler.enqueueDetail(context, regionKey, silhouette.assetId)
                    withContext(Dispatchers.IO) {
                        LocalMediaStore(context).downloadNow(
                            content.generation().generationId,
                            silhouette,
                            "detail",
                        )
                    }
                    applyProjection(uiState.enrichmentMessage)
                }
            }
            loadObservationDensity()
        }
    }

    private fun loadObservationDensity(force: Boolean = false) {
        if (taxonId <= 0 || densityJob?.isActive == true) return
        uiState = uiState.copy(observationDensityLoading = true, observationDensityMessage = null)
        densityJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                ObservationDensityStore(getApplication()).load(taxonId, force)
            }
            densitySnapshot = result.snapshot
            densityMessage = when {
                result.stale -> "Showing stored observation density; iNaturalist could not be reached."
                result.snapshot == null -> "Public observation density could not be loaded."
                result.snapshot.cells.isEmpty() -> "No mappable research-grade observations were found in this sample."
                else -> null
            }
            uiState = uiState.copy(
                observationDensity = densitySnapshot,
                observationDensityLoading = false,
                observationDensityMessage = densityMessage,
            )
        }
    }

    private suspend fun applyProjection(message: String?) {
        val revision = loadGate.next()
        val loaded = withContext(Dispatchers.IO) { load(mediaAttempt) }
        if (!loadGate.isLatest(revision)) return
        publishedTaxon = loaded.published
        uiState = loaded.state.copy(
            isLoading = false,
            enrichmentMessage = message,
        )
    }

    private fun enrich(force: Boolean = false) {
        if (taxonId <= 0 || uiState.enriching) return
        uiState = uiState.copy(enriching = true, enrichmentMessage = null)
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    OnDeviceWildlifeRepository(getApplication()).use {
                        it.syncTaxonDetail(taxonId, force)
                    }
                }
            }
            loadState(
                enrichmentMessage = result.exceptionOrNull()?.let {
                    "Reference information could not be refreshed. Stored details remain available."
                },
            )
        }
    }

    private fun load(attempt: Int): LoadedDetail = runCatching {
        require(taxonId > 0) { "This species has no usable iNaturalist taxon ID." }
        val context = getApplication<Application>()
        val content = PublishedContentRepositories.application(context)
        val published = content.taxon(taxonId)
        val mediaStore = LocalMediaStore(context)
        val recovered = RemoteTaxonStore(context).use { it.cached(taxonId) }
        val details = published?.toLocalTaxonDetails(mediaStore, recovered) ?: recovered
        val catalogues = content.catalogues()
        val selectedKey = regionKeyForTaxon(content)
            ?: error("This species is not in an installed regional catalogue.")
        val selected = catalogues.first { it.regionKey == selectedKey }
        val regionalTaxon = content.taxa(selected.regionKey).firstOrNull { it.taxonId == taxonId }
        val achievementLabels = content.achievements(selected.regionKey)
            .filter { taxonId in it.taxonIds }
            .mapTo(linkedSetOf()) { it.label }
        val observations = AccountStore(context).verified()?.let { account ->
            ObservationStore(context).use { store ->
                store.observations(account.userId).filter { observation ->
                    store.observationRegion(account.userId, observation.uuid)?.let { assignment ->
                        assignment.earnsRegionalProgress && assignment.regionKey == selected.regionKey
                    } == true
                }
            }
        }.orEmpty()
        val projected = SpeciesDetailProjection.build(
            taxonId, fallbackLabel, observations, details,
            regionalTaxon = regionalTaxon,
            regionalCatalogueVersion = selected.version,
        )
        val state = projected.copy(
                regionalContext = regionalTaxon?.let {
                    SpeciesRegionalContext(
                        regionName = selected.displayName,
                        rarity = it.rarity,
                        achievementLabels = achievementLabels,
                    )
                },
                mediaRetryAvailable = if (published != null) {
                    val repairNeeds = published.mediaRepairNeeds()
                    published.media.isNotEmpty() ||
                        (repairNeeds.photo &&
                            recovered?.photoPipelineVersion !=
                            OnDeviceWildlifeRepository.MEDIA_PIPELINE_VERSION) ||
                        (repairNeeds.silhouette &&
                            recovered?.silhouetteResolverVersion !=
                            OnDeviceWildlifeRepository.SILHOUETTE_PIPELINE_VERSION)
                } else {
                    details?.photoRecoveryStatus != null &&
                        details.photoRecoveryStatus != "no_compatible_photo"
                },
                alternativeImageAvailable = published != null && projected.heroFromCatalogue,
                observationDensity = densitySnapshot,
                observationDensityMessage = densityMessage,
            )
            .copy(mediaAttempt = attempt)
        LoadedDetail(state, published = published != null)
    }.getOrElse { error ->
        LoadedDetail(
            emptyState(
                errorMessage = error.message ?: "Species details could not be loaded.",
                mediaAttempt = attempt,
            ),
            published = false,
        )
    }

    private fun emptyState(
        isLoading: Boolean = false,
        errorMessage: String? = null,
        mediaAttempt: Int = 0,
    ) = SpeciesDetailUiState(
        taxonId = taxonId,
        commonName = fallbackLabel ?: "Species",
        scientificName = null,
        taxonGroup = null,
        familyName = null,
        aboutSummary = null,
        wikipediaUrl = null,
        conservationStatus = null,
        conservationAuthority = null,
        conservationUrl = null,
        observed = false,
        researchGrade = false,
        bestQualityLabel = if (isLoading) "Loading" else "Unavailable",
        heroPhotoUrl = null,
        heroFromCatalogue = false,
        heroAttribution = null,
        silhouetteUrl = null,
        silhouetteSourceUrl = null,
        silhouetteAttribution = null,
        silhouetteLicenseCode = null,
        silhouetteTaxonName = null,
        silhouetteMatchRank = null,
        catalogueVersion = null,
        observations = emptyList(),
        errorMessage = errorMessage,
        mediaAttempt = mediaAttempt,
        isLoading = isLoading,
    )

    private data class LoadedDetail(
        val state: SpeciesDetailUiState,
        val published: Boolean,
    )

    private fun regionKeyForTaxon(content: RegionalCatalogueAssetStore): String? {
        val catalogues = content.catalogues()
        val containing = catalogues.filter { catalogue ->
            content.taxa(catalogue.regionKey).any { it.taxonId == taxonId }
        }
        requestedRegionKey?.takeIf { key -> containing.any { it.regionKey == key } }?.let { return it }
        val current = RegionContextStore(getApplication()).currentRegionKey(
            catalogues.mapTo(mutableSetOf()) { it.regionKey },
        )
        return current?.takeIf { key -> containing.any { it.regionKey == key } }
            ?: containing.firstOrNull()?.regionKey
    }
}
