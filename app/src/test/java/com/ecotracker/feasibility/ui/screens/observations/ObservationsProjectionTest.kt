package com.wildlife.feasibility.ui.screens.observations

import com.wildlife.feasibility.MarkerState
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

    private fun project(
        markers: List<PendingMarker> = emptyList(),
        observations: List<SyncedObservation> = emptyList(),
        hidden: Set<String> = emptySet(),
        taxonFilter: Long? = null,
        regions: Map<String, ObservationRegion> = emptyMap(),
    ) = ObservationsProjection.build(
        markers = markers,
        proposalsByMarker = emptyMap(),
        observations = observations,
        hiddenUuids = hidden,
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
