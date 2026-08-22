package com.wildlife.feasibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BetaMetricsTest {
    @Test
    fun `groups multi-photo handoffs and reports confirmed pending ambiguous and unmatched`() {
        val metrics = BetaMetricsProjection.project(
            markers = listOf(
                marker("one", MarkerState.PENDING, handoffId = "same"),
                marker("two", MarkerState.PENDING, handoffId = "same"),
                marker("three", MarkerState.CONFIRMED, handoffId = "done").copy(matchedObservationUuid = "matched"),
                marker("four", MarkerState.HANDED_OFF, handoffId = "unmatched").copy(capturedAtMs = 900_000_000L),
            ),
            candidates = listOf(
                candidate("a", 1_001),
                candidate("b", 1_002),
                candidate("matched", 1_001),
            ),
        )

        assertEquals(3, metrics.submittedHandoffs)
        assertEquals(1, metrics.confirmedHandoffs)
        assertEquals(2, metrics.pendingHandoffs)
        assertEquals(1, metrics.ambiguousPendingHandoffs)
        assertEquals(1, metrics.unmatchedPendingHandoffs)
        assertEquals(1f / 3f, metrics.handoffCompletionFraction ?: 0f, 0.0001f)
    }

    @Test
    fun `completion is unavailable before a handoff is submitted`() {
        assertNull(BetaMetricsProjection.project(emptyList(), emptyList()).handoffCompletionFraction)
    }

    private fun marker(id: String, state: MarkerState, handoffId: String) = PendingMarker(
        id = id, imageUri = "content://$id", source = "camera", capturedAtMs = 1_000,
        latitude = 41.0, longitude = 2.0, state = state, sharedAtMs = 1_500, handoffId = handoffId,
    )

    private fun candidate(uuid: String, observedAtMs: Long) = ObservationCandidate(
        uuid = uuid, observedAtMs = observedAtMs, latitude = 41.0, longitude = 2.0,
        obscured = false, createdAtMs = 2_000,
    )
}
