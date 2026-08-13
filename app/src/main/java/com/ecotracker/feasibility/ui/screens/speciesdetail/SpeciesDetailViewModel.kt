package com.wildlife.feasibility.ui.screens.speciesdetail

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.wildlife.feasibility.AccountStore
import com.wildlife.feasibility.CataloguePhotoPolicy
import com.wildlife.feasibility.CatalogueSnapshot
import com.wildlife.feasibility.CatalogueStore
import com.wildlife.feasibility.ObservationStore
import com.wildlife.feasibility.OnDeviceWildlifeRepository
import com.wildlife.feasibility.SyncedObservation
import com.wildlife.feasibility.TaxonDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val TAXON_ID_KEY = "species_taxon_id"
const val TAXON_LABEL_KEY = "species_fallback_label"

data class SpeciesDetailObservation(
    val uuid: String,
    val observedAtMs: Long,
    val qualityGrade: String,
    val photoUrl: String?,
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
    val mediaAttempt: Int = 0,
) {
    val latestObservedAtMs: Long? get() = observations.firstOrNull()?.observedAtMs
}

internal object SpeciesDetailProjection {
    fun build(
        taxonId: Long,
        fallbackLabel: String?,
        snapshot: CatalogueSnapshot?,
        observations: List<SyncedObservation>,
        details: TaxonDetails? = null,
    ): SpeciesDetailUiState {
        val catalogue = snapshot?.species?.firstOrNull { it.taxonId == taxonId }
        val personal = observations
            .filter { (it.collectionTaxonId ?: it.taxonId) == taxonId }
            .sortedByDescending(SyncedObservation::observedAtMs)
        val label = catalogue?.commonName ?: details?.commonName
            ?: personal.firstNotNullOfOrNull { it.label.takeIf(String::isNotBlank) }
            ?: fallbackLabel?.takeIf(String::isNotBlank)
            ?: catalogue?.scientificName ?: details?.scientificName ?: "Unknown species"
        val scientificName = (details?.scientificName ?: catalogue?.scientificName)
            ?.takeUnless { it == label }
        val personalPhoto = personal.firstNotNullOfOrNull(SyncedObservation::photoUrl)
        val cataloguePhoto = catalogue?.let(CataloguePhotoPolicy::detailUrl)?.let {
            catalogue.photoLocalUri ?: it
        }
        val detailPhoto = details?.let(CataloguePhotoPolicy::detailUrl)?.let {
            details.photoLocalUri ?: it
        }
        val referencePhoto = detailPhoto ?: cataloguePhoto
        val referencePhotoRemote = details?.let(CataloguePhotoPolicy::detailUrl)
            ?: catalogue?.let(CataloguePhotoPolicy::detailUrl)
        val silhouetteRemote = details?.silhouetteUrl ?: catalogue?.silhouetteUrl
        val silhouette = details?.silhouetteUrl?.let { details.silhouetteLocalUri ?: it }
            ?: catalogue?.silhouetteUrl?.let { catalogue.silhouetteLocalUri ?: it }
        val heroFromCatalogue = referencePhoto != null
        val bestQuality = personal.maxByOrNull { qualityRank(it.qualityGrade) }?.qualityGrade
        return SpeciesDetailUiState(
            taxonId = taxonId,
            commonName = label,
            scientificName = scientificName,
            taxonGroup = (details?.taxonGroup ?: catalogue?.taxonGroup)?.let(::friendlyTaxonGroup),
            familyName = details?.familyName ?: catalogue?.familyName,
            aboutSummary = details?.wikipediaSummary ?: catalogue?.wikipediaSummary,
            wikipediaUrl = details?.wikipediaUrl ?: catalogue?.wikipediaUrl,
            conservationStatus = details?.conservationStatus ?: catalogue?.conservationStatus,
            conservationAuthority = details?.conservationAuthority
                ?: catalogue?.conservationAuthority,
            conservationUrl = details?.conservationUrl ?: catalogue?.conservationUrl,
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
            heroAttribution = if (!heroFromCatalogue) null else if (detailPhoto != null) {
                details?.let(CataloguePhotoPolicy::attribution)
            } else {
                catalogue?.let(CataloguePhotoPolicy::attribution)
            },
            silhouetteUrl = silhouette,
            silhouetteFallbackUrl = silhouetteRemote?.takeIf { it != silhouette },
            silhouetteSourceUrl = details?.silhouetteSourceUrl ?: catalogue?.silhouetteSourceUrl,
            silhouetteAttribution = details?.silhouetteAttribution
                ?: catalogue?.silhouetteAttribution,
            silhouetteLicenseCode = details?.silhouetteLicenseCode
                ?: catalogue?.silhouetteLicenseCode,
            silhouetteTaxonName = details?.silhouetteTaxonName
                ?: catalogue?.silhouetteTaxonName,
            silhouetteMatchRank = details?.silhouetteMatchRank
                ?: catalogue?.silhouetteMatchRank,
            catalogueVersion = snapshot?.version,
            observations = personal.map {
                SpeciesDetailObservation(it.uuid, it.observedAtMs, it.qualityGrade, it.photoUrl)
            },
            referencePhotoSourceUrl = if (!heroFromCatalogue) null else {
                details?.photoSourceUrl ?: "https://www.inaturalist.org/taxa/$taxonId"
            },
            photoRecoveryStatus = details?.photoRecoveryStatus,
            referencePhotoSourceLabel = when (details?.photoRecoveryStatus) {
                "wikimedia_featured", "wikimedia_quality" ->
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
    private var mediaAttempt = 0

    var uiState by mutableStateOf(load())
        private set

    init {
        enrich()
    }

    fun refresh() {
        if (!uiState.enriching) uiState = load()
    }

    fun retryMedia() {
        if (uiState.enriching) return
        mediaAttempt++
        enrich(force = true)
    }

    private fun enrich(force: Boolean = false) {
        if (taxonId <= 0 || uiState.enriching) return
        uiState = uiState.copy(enriching = true, enrichmentMessage = null)
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    OnDeviceWildlifeRepository(getApplication()).syncTaxonDetail(taxonId, force)
                }
            }
            uiState = result.fold(
                onSuccess = { load() },
                onFailure = {
                    load().copy(
                        enrichmentMessage =
                            "Reference information could not be refreshed. Stored details remain available.",
                    )
                },
            )
        }
    }

    private fun load(): SpeciesDetailUiState = runCatching {
        require(taxonId > 0) { "This species has no usable iNaturalist taxon ID." }
        val context = getApplication<Application>()
        val store = CatalogueStore(context)
        val snapshot = store.load()
        val details = store.loadTaxonDetail(taxonId)
        val observations = AccountStore(context).verified()?.let { account ->
            ObservationStore(context).observations(account.userId)
        }.orEmpty()
        SpeciesDetailProjection.build(taxonId, fallbackLabel, snapshot, observations, details)
            .copy(mediaAttempt = mediaAttempt)
    }.getOrElse { error ->
        SpeciesDetailUiState(
            taxonId = taxonId, commonName = fallbackLabel ?: "Species", scientificName = null,
            taxonGroup = null, familyName = null, aboutSummary = null, wikipediaUrl = null,
            conservationStatus = null, conservationAuthority = null, conservationUrl = null,
            observed = false, researchGrade = false, bestQualityLabel = "Unavailable",
            heroPhotoUrl = null, heroFromCatalogue = false, heroAttribution = null,
            silhouetteUrl = null, silhouetteSourceUrl = null, silhouetteAttribution = null,
            silhouetteLicenseCode = null, silhouetteTaxonName = null,
            silhouetteMatchRank = null, catalogueVersion = null, observations = emptyList(),
            errorMessage = error.message ?: "Species details could not be loaded.",
            mediaAttempt = mediaAttempt,
        )
    }
}
