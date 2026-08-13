package com.wildlife.feasibility.ui.screens.map

import com.wildlife.feasibility.SyncedObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalObservationMapProjectionTest {
    @Test
    fun `nearby observations are aggregated into a coarse privacy cell`() {
        val map = PersonalObservationMapProjection.build(
            listOf(
                observation("one", 41.381, 2.171, "needs_id", obscured = false),
                observation("two", 41.389, 2.179, "research", obscured = true),
            ),
        )

        assertEquals(1, map.cells.size)
        assertEquals(2, map.cells.single().observationCount)
        assertEquals(1, map.cells.single().researchGradeCount)
        assertEquals(1, map.cells.single().obscuredCount)
        assertEquals(2, map.mappedObservationCount)
    }

    @Test
    fun `missing and invalid coordinates remain visible in the map summary`() {
        val map = PersonalObservationMapProjection.build(
            listOf(
                observation("mapped", 41.38, 2.17, "needs_id"),
                observation("missing", null, null, "needs_id"),
                observation("invalid", 120.0, 2.0, "needs_id"),
            ),
        )

        assertEquals(1, map.mappedObservationCount)
        assertEquals(2, map.unavailableLocationCount)
    }

    @Test
    fun `user hidden observations are excluded without becoming unavailable`() {
        val map = PersonalObservationMapProjection.build(
            observations = listOf(
                observation("visible", 41.38, 2.17, "needs_id"),
                observation("hidden", null, null, "research"),
            ),
            hiddenObservationUuids = setOf("hidden"),
        )

        assertEquals(1, map.mappedObservationCount)
        assertEquals(1, map.hiddenObservationCount)
        assertEquals(0, map.unavailableLocationCount)
        assertEquals(false, map.observations.first { it.uuid == "hidden" }.mapVisible)
    }

    @Test
    fun `a single cell receives a non zero viewport`() {
        val map = PersonalObservationMapProjection.build(
            listOf(observation("one", 41.38, 2.17, "research")),
        )

        assertTrue(map.maxLatitude > map.minLatitude)
        assertTrue(map.maxLongitude > map.minLongitude)
    }

    @Test
    fun `a null-like upstream label is presented as unidentified`() {
        val map = PersonalObservationMapProjection.build(
            listOf(observation("one", 41.38, 2.17, "needs_id").copy(label = "null")),
        )

        assertEquals("Unidentified observation", map.cells.single().latestLabel)
    }

    private fun observation(
        uuid: String,
        latitude: Double?,
        longitude: Double?,
        qualityGrade: String,
        obscured: Boolean = false,
    ) = SyncedObservation(
        id = uuid.hashCode().toLong(), uuid = uuid, taxonId = 42, taxonRank = "species",
        collectionTaxonId = 42, collectionTaxonRank = "species", label = "European robin",
        observedAtMs = uuid.hashCode().toLong(), latitude = latitude, longitude = longitude,
        obscured = obscured, createdAtMs = 0, qualityGrade = qualityGrade, photoUrl = null,
        confirmed = false,
    )
}
