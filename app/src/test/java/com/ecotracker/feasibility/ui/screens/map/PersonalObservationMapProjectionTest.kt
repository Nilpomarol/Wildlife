package com.wildlife.feasibility.ui.screens.map

import com.wildlife.feasibility.InstalledRegionalAchievement
import com.wildlife.feasibility.InstalledRegionalCatalogue
import com.wildlife.feasibility.InstalledRegionalTaxon
import com.wildlife.feasibility.ObservationRegion
import com.wildlife.feasibility.ObservationRegionAssignment
import com.wildlife.feasibility.SyncedObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `regional map progress only counts assigned catalogue taxa`() {
        val mediterranean = catalogue("mediterranean", "Mediterranean")
        val caribbean = catalogue("caribbean", "Caribbean")
        val observations = listOf(
            observation("med-robin", 41.38, 2.17, "research").copy(collectionTaxonId = 1),
            observation("med-duplicate", 41.39, 2.18, "needs_id").copy(collectionTaxonId = 1),
            observation("caribbean", 18.47, -66.11, "needs_id").copy(collectionTaxonId = 2),
            observation("unassigned", null, null, "needs_id").copy(collectionTaxonId = 3),
            observation("outside-guide", 41.4, 2.2, "needs_id").copy(collectionTaxonId = 99),
        )

        val progress = RegionalMapProgressProjection.build(
            catalogues = listOf(mediterranean, caribbean),
            taxaByRegion = mapOf(
                "mediterranean" to listOf(taxon(1), taxon(3)),
                "caribbean" to listOf(taxon(2)),
            ),
            achievementsByRegion = mapOf(
                "mediterranean" to listOf(
                    InstalledRegionalAchievement("ESSENTIALS", setOf(1)),
                    InstalledRegionalAchievement("icons", setOf(1, 3)),
                ),
            ),
            observations = observations,
            assignmentsByObservationUuid = mapOf(
                "med-robin" to assignment("med-robin", "mediterranean"),
                "med-duplicate" to assignment("med-duplicate", "mediterranean"),
                "caribbean" to assignment("caribbean", "caribbean"),
                "outside-guide" to assignment("outside-guide", "mediterranean"),
            ),
        )

        assertEquals(1, progress[0].observedSpecies)
        assertEquals(2, progress[0].totalSpecies)
        assertTrue(progress[0].essentialsComplete)
        assertFalse(progress[0].iconsComplete)
        assertEquals(1, progress[1].observedSpecies)
        assertEquals(1f, progress[1].completionFraction)
    }

    @Test
    fun `uncertain and marine assignments do not contribute to regional map progress`() {
        val progress = RegionalMapProgressProjection.build(
            catalogues = listOf(catalogue("mediterranean", "Mediterranean")),
            taxaByRegion = mapOf("mediterranean" to listOf(taxon(1))),
            achievementsByRegion = emptyMap(),
            observations = listOf(
                observation("uncertain", 41.38, 2.17, "needs_id").copy(collectionTaxonId = 1),
                observation("marine", 41.38, 2.17, "needs_id").copy(collectionTaxonId = 1),
            ),
            assignmentsByObservationUuid = mapOf(
                "uncertain" to assignment("uncertain", "mediterranean", ObservationRegionAssignment.REGION_UNCERTAIN),
                "marine" to assignment("marine", null, ObservationRegionAssignment.MARINE_WORLDWIDE),
            ),
        ).single()

        assertEquals(0, progress.observedSpecies)
        assertEquals(0f, progress.completionFraction)
    }

    private fun catalogue(regionKey: String, displayName: String) =
        InstalledRegionalCatalogue(regionKey, displayName, "test")

    private fun taxon(id: Long) = InstalledRegionalTaxon(
        taxonId = id,
        commonName = "Test species $id",
        scientificName = "Testus species$id",
        rarity = com.wildlife.feasibility.EncounterRarity.COMMON,
        prestige = com.wildlife.feasibility.RegionalPrestige.STANDARD,
        taxonClass = "Aves",
    )

    private fun assignment(
        uuid: String,
        regionKey: String?,
        assignment: ObservationRegionAssignment = ObservationRegionAssignment.LAND_POLYGON,
    ) = ObservationRegion(uuid, regionKey, "test", assignment)

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
