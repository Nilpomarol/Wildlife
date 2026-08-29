package com.wildlife.feasibility.ui.screens.observations

import com.wildlife.feasibility.MarkerState
import com.wildlife.feasibility.MatchBand
import com.wildlife.feasibility.MatchProposal
import com.wildlife.feasibility.MatchReason
import com.wildlife.feasibility.ObservationCandidate
import com.wildlife.feasibility.ObservationRegion
import com.wildlife.feasibility.ObservationRegionAssignment
import com.wildlife.feasibility.PendingMarker
import com.wildlife.feasibility.SyncedObservation
import org.junit.Assert.assertEquals
import org.junit.Test

class ObservationsProjectionTest {
    @Test
    fun `groups multiple draft photos into one managed observation`() {
        val state = project(
            markers = listOf(
                marker("first", 100, MarkerState.PENDING, "handoff"),
                marker("second", 200, MarkerState.PENDING, "handoff"),
            ),
        )

        assertEquals(1, state.managed.size)
        assertEquals("handoff", state.managed.single().groupId)
        assertEquals(listOf("first", "second"), state.managed.single().photos.map { it.markerId })
    }

    @Test
    fun `filters public history to the requested species`() {
        val state = project(
            observations = listOf(observation("robin", 42), observation("fox", 99)),
            taxonFilter = 42,
        )

        assertEquals(listOf("robin"), state.publicObservations.map { it.uuid })
    }

    @Test
    fun `hides app-linked observations from the public history`() {
        val state = project(
            markers = listOf(
                marker("m1", 100, MarkerState.CONFIRMED, "handoff").copy(matchedObservationUuid = "robin"),
            ),
            observations = listOf(observation("robin", 42), observation("fox", 99)),
        )

        assertEquals(listOf("fox"), state.publicObservations.map { it.uuid })
        assertEquals("Test species", state.managed.single().label)
    }

    @Test
    fun `unfiltered history retains public observations`() {
        val state = project(observations = listOf(observation("robin", 42)))

        assertEquals("robin", state.publicObservations.single().uuid)
        assertEquals("research", state.publicObservations.single().qualityGrade)
    }

    @Test
    fun `shows a truthful regional assignment rather than inferring progress`() {
        val state = project(
            observations = listOf(observation("robin", 42)),
            regions = mapOf(
                "robin" to ObservationRegion(
                    "robin", "mediterranean_europe", "regional-boundaries-v1",
                    ObservationRegionAssignment.LAND_POLYGON,
                ),
            ),
        )

        assertEquals("Region · Mediterranean Europe", state.publicObservations.single().regionalContext?.label)
        assertEquals(true, state.publicObservations.single().regionalContext?.countsTowardRegion)
    }

    @Test
    fun `an automatically filed record leads the ledger and offers its comparison`() {
        val state = project(
            markers = listOf(
                marker("m1", 100, MarkerState.CONFIRMED, "handoff")
                    .copy(matchedObservationUuid = "robin", autoMatched = true),
            ),
            observations = listOf(observation("robin", 42).copy(photoUrl = "https://inat/robin.jpg")),
        )

        val filed = state.autoFiled.single()
        assertEquals("handoff", filed.groupId)
        // The public photograph is what makes the record checkable rather than announced.
        assertEquals("https://inat/robin.jpg", filed.matchedPhotoUrl)
        // It leads its own pile and appears in no other.
        assertEquals(emptyList<String>(), state.filed.map { it.groupId })
        assertEquals(emptyList<String>(), state.needsAction.map { it.groupId })
    }

    @Test
    fun `an answered automatic match settles into the filed pile`() {
        val state = project(
            markers = listOf(
                marker("m1", 100, MarkerState.CONFIRMED, "handoff")
                    .copy(matchedObservationUuid = "robin", autoMatched = false),
            ),
            observations = listOf(observation("robin", 42)),
        )

        assertEquals(emptyList<String>(), state.autoFiled.map { it.groupId })
        assertEquals(listOf("handoff"), state.filed.map { it.groupId })
    }

    @Test
    fun `a proposal carries the public record's own identity to compare against`() {
        val proposal = MatchProposal(
            markerId = "m1",
            candidate = ObservationCandidate(
                uuid = "robin", observedAtMs = 150, latitude = null, longitude = null,
                obscured = true, createdAtMs = 120, label = "European robin",
                photoUrl = "https://inat/robin.jpg",
            ),
            confidence = 0.72,
            band = MatchBand.LIKELY,
            timeDeltaMinutes = 4,
            distanceKm = null,
            reasons = listOf(MatchReason.LOCATION_OBSCURED),
        )

        val state = project(
            markers = listOf(marker("m1", 100, MarkerState.PENDING, "handoff")),
            proposals = mapOf("m1" to listOf(proposal)),
        )

        val match = state.needsAction.single().proposals.single()
        assertEquals("European robin", match.label)
        assertEquals("https://inat/robin.jpg", match.photoUrl)
        assertEquals(MatchBand.LIKELY, match.band)
        assertEquals(listOf(MatchReason.LOCATION_OBSCURED), match.reasons)
        // A capture with a live proposal is work, not something merely waiting.
        assertEquals(emptyList<String>(), state.awaiting.map { it.groupId })
    }

    @Test
    fun `a handoff with no proposal still asks whether it was submitted`() {
        val state = project(
            markers = listOf(marker("m1", 100, MarkerState.HANDED_OFF, "handoff")),
        )

        assertEquals(listOf("handoff"), state.needsAction.map { it.groupId })
        assertEquals(1, state.outstandingCount)
    }

    @Test
    fun `a submitted capture with nothing to match is merely awaiting`() {
        val state = project(
            markers = listOf(marker("m1", 100, MarkerState.PENDING, "handoff")),
        )

        assertEquals(listOf("handoff"), state.awaiting.map { it.groupId })
        assertEquals(0, state.outstandingCount)
    }

    private fun project(
        markers: List<PendingMarker> = emptyList(),
        observations: List<SyncedObservation> = emptyList(),
        taxonFilter: Long? = null,
        regions: Map<String, ObservationRegion> = emptyMap(),
        proposals: Map<String, List<MatchProposal>> = emptyMap(),
    ) = ObservationsProjection.build(
        markers = markers,
        proposalsByMarker = proposals,
        observations = observations,
        account = null,
        syncing = false,
        message = null,
        taxonFilter = taxonFilter,
        regionsByObservationUuid = regions,
    )

    private fun marker(id: String, time: Long, state: MarkerState, handoffId: String) = PendingMarker(
        id = id, imageUri = "content://photo/$id", source = "import", capturedAtMs = time,
        latitude = null, longitude = null, state = state, handoffId = handoffId,
    )

    private fun observation(uuid: String, taxonId: Long) = SyncedObservation(
        id = taxonId, uuid = uuid, taxonId = taxonId, taxonRank = "species",
        collectionTaxonId = taxonId, collectionTaxonRank = "species", label = "Test species",
        observedAtMs = 100, latitude = null, longitude = null, obscured = false,
        createdAtMs = 101, qualityGrade = "research", photoUrl = null, confirmed = true,
    )
}
