package com.wildlife.feasibility.ui.screens.capture

import com.wildlife.feasibility.MarkerState
import com.wildlife.feasibility.MatchBand
import com.wildlife.feasibility.MatchProposal
import com.wildlife.feasibility.ObservationCandidate
import com.wildlife.feasibility.PendingMarker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureProjectionTest {
    @Test
    fun groupsHandoffPhotosIntoOneObservation() {
        val first = marker("first", 100, MarkerState.PENDING, "handoff")
        val second = marker("second", 200, MarkerState.PENDING, "handoff")

        val state = project(listOf(first, second))

        assertEquals(1, state.observations.size)
        assertEquals(listOf("first", "second"), state.observations.single().photos.map { it.markerId })
    }

    @Test
    fun countsOnlySelectedDraftPhotos() {
        val draft = marker("draft", 100, MarkerState.CAPTURED)
        val pending = marker("pending", 200, MarkerState.PENDING, "handoff")

        val state = project(listOf(draft, pending), selected = setOf("draft", "pending"))

        assertEquals(1, state.selectedDraftPhotos)
        assertTrue(
            state.observations.single { it.groupId == CaptureProjection.DRAFT_GROUP_ID }
                .photos.single().selected,
        )
        assertTrue(state.observations.single { it.groupId == "handoff" }.photos.single().selected)
    }

    @Test
    fun presentsAllDraftPhotosAsOneObservation() {
        val first = marker("first", 100, MarkerState.CAPTURED)
        val second = marker("second", 200, MarkerState.CAPTURED)

        val state = project(listOf(first, second))

        assertEquals(1, state.observations.size)
        assertEquals(CaptureProjection.DRAFT_GROUP_ID, state.observations.single().groupId)
        assertEquals(listOf("first", "second"), state.observations.single().photos.map { it.markerId })
        assertEquals(2, state.selectedDraftPhotos)
        assertTrue(state.observations.single().photos.all { it.selected })
    }

    @Test
    fun attachesCandidateToTheRepresentativePhoto() {
        val marker = marker("marker", 100, MarkerState.PENDING, "handoff")
        val proposal = MatchProposal(
            markerId = marker.id,
            candidate = ObservationCandidate("uuid", 100, null, null, true, 150),
            confidence = 0.61,
            band = MatchBand.POSSIBLE,
            timeDeltaMinutes = 0,
            distanceKm = null,
        )

        val state = project(listOf(marker), proposals = mapOf(marker.id to listOf(proposal)))

        assertEquals(listOf(proposal), state.observations.single().proposals)
        assertFalse(state.busy)
    }

    private fun project(
        markers: List<PendingMarker>,
        selected: Set<String> = emptySet(),
        proposals: Map<String, List<MatchProposal>> = emptyMap(),
    ) = CaptureProjection.build(
        markers = markers,
        selectedMarkerIds = selected,
        proposalsByMarker = proposals,
        account = null,
        statusMessage = "Ready",
        busy = false,
        reward = null,
    )

    private fun marker(
        id: String,
        capturedAt: Long,
        state: MarkerState,
        handoffId: String? = null,
    ) = PendingMarker(
        id = id,
        imageUri = "content://photo/$id",
        source = "import",
        capturedAtMs = capturedAt,
        latitude = null,
        longitude = null,
        state = state,
        handoffId = handoffId,
    )
}
