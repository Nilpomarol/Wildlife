package com.wildlife.feasibility

data class INaturalistUser(
    val id: Long,
    val login: String,
)

data class PublicINaturalistProfile(
    val id: Long,
    val login: String,
    val description: String?,
)

data class PendingAccountLink(
    val userId: Long,
    val login: String,
    val code: String,
    val createdAtMs: Long,
)

data class VerifiedAccount(
    val userId: Long,
    val login: String,
    val verifiedAtMs: Long,
)

data class PendingMarker(
    val id: String,
    val imageUri: String,
    val source: String,
    val capturedAtMs: Long,
    val latitude: Double?,
    val longitude: Double?,
    val state: MarkerState = MarkerState.CAPTURED,
    val sharedAtMs: Long? = null,
    val matchedObservationUuid: String? = null,
    val handoffId: String? = null,
    val capturedAtReliable: Boolean = true,
    val locationReliable: Boolean = latitude != null && longitude != null,
)

enum class MarkerState {
    CAPTURED,
    HANDED_OFF,
    PENDING,
    CONFIRMED,
}

data class ObservationCandidate(
    val uuid: String,
    val observedAtMs: Long,
    val latitude: Double?,
    val longitude: Double?,
    val obscured: Boolean,
    val createdAtMs: Long? = null,
)

data class SyncedObservation(
    val id: Long,
    val uuid: String,
    val taxonId: Long?,
    val taxonRank: String?,
    val collectionTaxonId: Long?,
    val collectionTaxonRank: String?,
    val label: String,
    val observedAtMs: Long,
    val latitude: Double?,
    val longitude: Double?,
    val obscured: Boolean,
    val createdAtMs: Long?,
    val qualityGrade: String,
    val photoUrl: String?,
    val confirmed: Boolean,
) {
    fun asCandidate() = ObservationCandidate(
        uuid = uuid,
        observedAtMs = observedAtMs,
        latitude = latitude,
        longitude = longitude,
        obscured = obscured,
        createdAtMs = createdAtMs,
    )
}

data class CollectionSummary(
    val confirmedCount: Int = 0,
    val totalXp: Int = 0,
    val lastSyncedAtMs: Long? = null,
)

data class ObservationSyncResult(
    val observations: List<SyncedObservation>,
    val summary: CollectionSummary,
    val cached: Boolean,
    val qualityTransitions: List<ObservationQualityTransition> = emptyList(),
)

data class ObservationQualityTransition(
    val observationUuid: String,
    val label: String,
    val fromQualityGrade: String,
    val toQualityGrade: String,
    val detectedAtMs: Long,
)

/**
 * Maps a Linnaean class to the app's normalized, filterable group key.
 *
 * The same vocabulary serves two sources: the frozen catalogue's `taxonClass` and
 * iNaturalist's `iconic_taxon_name`, which agree on these five groups. Group keys are what
 * `assets/taxon-glyphs/` is named by, so anything that will draw a silhouette must go
 * through here first — passing a raw "Aves" to the silhouette loader finds no asset and
 * renders nothing at all, with no error.
 *
 * Lives in the data layer rather than in a ViewModel because parsing normalizes at the
 * boundary now, and a UI-layer function cannot be called from the API client.
 */
fun taxonGroupForClass(taxonClass: String?): String? = when (taxonClass) {
    "Mammalia" -> "mammals"
    "Aves" -> "birds"
    "Reptilia" -> "reptiles"
    "Amphibia" -> "amphibians"
    "Actinopterygii", "Chondrichthyes", "Myxini", "Petromyzonti" -> "fish"
    // Already normalized, e.g. a value restored from the nearby cache.
    "mammals", "birds", "reptiles", "amphibians", "fish" -> taxonClass
    else -> null
}

data class NearbySpecies(
    val taxonId: Long,
    val commonName: String?,
    val scientificName: String,
    val taxonGroup: String?,
    val observationCount: Int,
    /**
     * The taxon's default photo, straight off the species_counts response.
     *
     * Carried unfiltered; [CataloguePhotoPolicy.cardUrl] decides whether it may actually be
     * shown. The licence and attribution travel with it because that decision cannot be made
     * without them, and a URL that arrives without its licence must never be rendered.
     */
    val photoUrl: String? = null,
    val photoAttribution: String? = null,
    val photoLicenseCode: String? = null,
    /** Region-scoped user sighting selected by the local projection; never provider media. */
    val personalPhotoUrl: String? = null,
    /** Most specific validated local catalogue silhouette, with the group glyph beneath it. */
    val silhouetteUrl: String? = null,
    val silhouetteMatchRank: String? = null,
) {
    /** The photo if its licence permits uncredited card use, otherwise null. CC0 only. */
    fun cardPhotoUrl(): String? =
        CataloguePhotoPolicy.cardUrl(photoUrl, photoAttribution, photoLicenseCode)

    /**
     * The photo for a surface that shows a credit line, which is the wider set.
     *
     * Callers must render [tileAttribution] alongside it; a tile that shows this URL without
     * the credit is a licence breach, not a layout choice.
     */
    fun tilePhotoUrl(): String? =
        CataloguePhotoPolicy.creditedUrl(photoUrl, photoAttribution, photoLicenseCode)

    /** The credit that must appear with [tilePhotoUrl], shortened to one narrow line. */
    fun tileAttribution(): String? {
        if (tilePhotoUrl() == null) return null
        return CataloguePhotoPolicy.compactCredit(photoAttribution, photoLicenseCode)
    }
}

data class ObservationConfirmationResult(
    val xpAwarded: Int,
    val summary: CollectionSummary,
)

data class TaxonDetails(
    val taxonId: Long,
    val scientificName: String,
    val commonName: String?,
    val taxonGroup: String?,
    val familyName: String?,
    val wikipediaSummary: String?,
    val wikipediaUrl: String?,
    val conservationStatus: String?,
    val conservationAuthority: String?,
    val conservationUrl: String?,
    val photoUrl: String?,
    val photoAttribution: String?,
    val photoLicenseCode: String?,
    val silhouetteUrl: String?,
    val silhouetteSourceUrl: String?,
    val silhouetteAttribution: String?,
    val silhouetteLicenseCode: String?,
    val silhouetteLicenseUrl: String?,
    val silhouetteTaxonName: String?,
    val silhouetteMatchRank: String?,
    val updatedAtMs: Long?,
    val photoSourceUrl: String? = null,
    val photoRecoveryStatus: String? = null,
    val mediaPipelineVersion: Int? = null,
    val photoLocalUri: String? = null,
    val silhouetteLocalUri: String? = null,
    val photoPipelineVersion: Int? = null,
    val silhouetteResolverVersion: Int? = null,
    val silhouetteFallbackUrl: String? = null,
    val silhouetteFallbackSourceUrl: String? = null,
    val silhouetteFallbackAttribution: String? = null,
    val silhouetteFallbackLicenseCode: String? = null,
    val silhouetteFallbackLicenseUrl: String? = null,
    val silhouetteFallbackTaxonName: String? = null,
    val silhouetteFallbackMatchRank: String? = null,
)

data class CollectionSpecies(
    val key: String,
    val taxonId: Long?,
    val label: String,
    val observationCount: Int,
    val rewardedObservationCount: Int,
    val latestObservationUuid: String,
    val latestObservedAtMs: Long,
    val bestQualityGrade: String,
    val awaitingSpeciesIdentification: Boolean,
    val photoUrl: String?,
    /** Licence-verified silhouette cached by the shared catalogue media pipeline. */
    val silhouetteUrl: String? = null,
    val silhouetteFallbackUrl: String? = null,
    val silhouetteMatchRank: String? = null,
    val regionalEssential: Boolean = false,
    val regionalIcon: Boolean = false,
    val scientificName: String? = null,
    val encounterRarity: EncounterRarity? = null,
    /** Normalized taxonomic group key: mammals, birds, reptiles, amphibians, fish; null if unknown. */
    val taxonGroup: String? = null,
)

object CollectionProjection {
    fun species(observations: List<SyncedObservation>): List<CollectionSpecies> = observations
        .groupBy { observation ->
            observation.collectionTaxonId?.let { "taxon:$it" }
                ?: observation.taxonId?.let { "taxon:$it" }
                ?: "observation:${observation.uuid}"
        }
        .map { (key, sightings) ->
            val latest = sightings.maxBy(SyncedObservation::observedAtMs)
            CollectionSpecies(
                key = key,
                taxonId = latest.collectionTaxonId ?: latest.taxonId,
                label = sightings.firstNotNullOfOrNull { it.label.takeIf(String::isNotBlank) }
                    ?: "Unidentified",
                observationCount = sightings.size,
                rewardedObservationCount = sightings.count(SyncedObservation::confirmed),
                latestObservationUuid = latest.uuid,
                latestObservedAtMs = latest.observedAtMs,
                bestQualityGrade = sightings.maxBy { qualityRank(it.qualityGrade) }.qualityGrade,
                awaitingSpeciesIdentification = latest.collectionTaxonRank != "species",
                photoUrl = sightings.firstNotNullOfOrNull(SyncedObservation::photoUrl),
            )
        }
        .sortedBy { it.label.lowercase() }

    private fun qualityRank(value: String): Int = when (value) {
        "research" -> 3
        "needs_id" -> 2
        "casual" -> 1
        else -> 0
    }

    fun observedSpeciesTaxonIds(observations: List<SyncedObservation>): Set<Long> = observations
        .asSequence()
        .filter { it.collectionTaxonRank == "species" }
        .mapNotNull { it.collectionTaxonId }
        .toSet()
}

enum class MatchConfidence {
    HIGH,
    NEEDS_CONFIRMATION,
}

data class MatchProposal(
    val markerId: String,
    val candidate: ObservationCandidate,
    val confidence: MatchConfidence,
    val timeDeltaMinutes: Long,
    val distanceKm: Double?,
)
