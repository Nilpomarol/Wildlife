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

data class PublicObservation(
    val id: Long,
    val uuid: String,
    val label: String,
    val observedOn: String,
    val qualityGrade: String,
)

data class PublicObservationPage(
    val totalResults: Int,
    val observations: List<PublicObservation>,
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

data class BackendSyncResult(
    val observations: List<SyncedObservation>,
    val summary: CollectionSummary,
    val cached: Boolean,
)

data class BackendConfirmationResult(
    val xpAwarded: Int,
    val summary: CollectionSummary,
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
            )
        }
        .sortedBy { it.label.lowercase() }

    private fun qualityRank(value: String): Int = when (value) {
        "research" -> 3
        "needs_id" -> 2
        "casual" -> 1
        else -> 0
    }
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
