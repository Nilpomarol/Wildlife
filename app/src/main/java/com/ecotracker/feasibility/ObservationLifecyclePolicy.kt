package com.wildlife.feasibility

data class PendingHandoffStatus(
    val awaitingPublicRecord: Int,
    val readyToReview: Int,
)

object ObservationLifecyclePolicy {
    const val STALE_AFTER_MS = 24L * 60L * 60L * 1_000L

    fun isStale(lastSyncedAtMs: Long?, nowMs: Long): Boolean =
        lastSyncedAtMs == null || nowMs - lastSyncedAtMs > STALE_AFTER_MS

    fun pendingStatus(
        markers: List<PendingMarker>,
        candidates: List<ObservationCandidate>,
    ): PendingHandoffStatus {
        val alreadyMatched = markers.asSequence()
            .filter { it.state == MarkerState.CONFIRMED }
            .mapNotNull(PendingMarker::matchedObservationUuid)
            .toSet()
        val representatives = markers.asSequence()
            .filter { it.state == MarkerState.PENDING }
            .groupBy { it.handoffId ?: it.id }
            .values
            .map { group -> group.minBy(PendingMarker::capturedAtMs) }
        // Resolved as one set rather than one capture at a time, so two captures competing
        // for the same record are not both counted as ready.
        val proposals = CandidateMatcher.assign(representatives, candidates, alreadyMatched).all()
        return PendingHandoffStatus(
            awaitingPublicRecord = representatives.size,
            readyToReview = representatives.count { !proposals[it.id].isNullOrEmpty() },
        )
    }

    fun qualityTransition(
        previousQualityGrade: String?,
        observation: SyncedObservation,
        detectedAtMs: Long,
    ): ObservationQualityTransition? {
        if (previousQualityGrade == null || previousQualityGrade == observation.qualityGrade) {
            return null
        }
        return ObservationQualityTransition(
            observationUuid = observation.uuid,
            label = observation.label,
            fromQualityGrade = previousQualityGrade,
            toQualityGrade = observation.qualityGrade,
            detectedAtMs = detectedAtMs,
        )
    }
}
