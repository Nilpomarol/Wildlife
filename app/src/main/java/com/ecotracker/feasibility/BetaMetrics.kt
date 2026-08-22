package com.wildlife.feasibility

/** Local-only aggregate measures for the closed beta; no identity, species or coordinates escape. */
data class BetaMetrics(
    val submittedHandoffs: Int = 0,
    val confirmedHandoffs: Int = 0,
    val pendingHandoffs: Int = 0,
    val unmatchedPendingHandoffs: Int = 0,
    val ambiguousPendingHandoffs: Int = 0,
) {
    val handoffCompletionFraction: Float?
        get() = submittedHandoffs.takeIf { it > 0 }?.let {
            confirmedHandoffs.toFloat() / it.toFloat()
        }

    fun privacySafeReport(): String = buildString {
        appendLine("Wildlife closed-beta metrics")
        appendLine("Submitted handoffs: $submittedHandoffs")
        appendLine("Confirmed handoffs: $confirmedHandoffs")
        appendLine("Pending handoffs: $pendingHandoffs")
        appendLine("Pending handoffs without a candidate: $unmatchedPendingHandoffs")
        appendLine("Ambiguous pending handoffs: $ambiguousPendingHandoffs")
        append("Handoff completion: ${handoffCompletionFraction?.let { "%.1f%%".format(it * 100) } ?: "not available"}")
    }
}

internal object BetaMetricsProjection {
    fun project(
        markers: List<PendingMarker>,
        candidates: List<ObservationCandidate>,
    ): BetaMetrics {
        val handoffs = markers.asSequence()
            .filter { it.state != MarkerState.CAPTURED }
            .groupBy { it.handoffId ?: it.id }
            .values
            .map { group -> group.minBy(PendingMarker::capturedAtMs) }
        val confirmed = handoffs.count { it.state == MarkerState.CONFIRMED }
        val pending = handoffs.filter { it.state == MarkerState.HANDED_OFF || it.state == MarkerState.PENDING }
        val matchedElsewhere = handoffs.asSequence()
            .filter { it.state == MarkerState.CONFIRMED }
            .mapNotNull(PendingMarker::matchedObservationUuid)
            .toSet()
        val proposals = pending.associateWith { marker ->
            CandidateMatcher.proposals(marker, candidates.filterNot { it.uuid in matchedElsewhere })
        }
        return BetaMetrics(
            submittedHandoffs = handoffs.size,
            confirmedHandoffs = confirmed,
            pendingHandoffs = pending.size,
            unmatchedPendingHandoffs = proposals.count { (_, matches) -> matches.isEmpty() },
            ambiguousPendingHandoffs = proposals.count { (_, matches) -> matches.size > 1 },
        )
    }
}
