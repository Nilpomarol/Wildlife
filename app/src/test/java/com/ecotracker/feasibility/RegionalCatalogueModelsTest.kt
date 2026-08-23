package com.wildlife.feasibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionalCatalogueModelsTest {
    @Test
    fun `encounter rarity is the only tier a regional taxon carries`() {
        val elephant = RegionalTaxon(
            regionKey = "east_africa", catalogueVersion = "draft-1", taxonId = 123,
            encounterRarity = EncounterRarity.COMMON,
            inclusionProvenance = "reviewed pilot example",
        )

        assertEquals(EncounterRarity.COMMON, elephant.encounterRarity)
    }

    @Test
    fun `only land and one matching offshore buffer earn regional progress`() {
        val land = ObservationRegion("one", "east_africa", "v1", ObservationRegionAssignment.LAND_POLYGON)
        val offshore = ObservationRegion("two", "east_africa", "v1", ObservationRegionAssignment.OFFSHORE_BUFFER)
        val marine = ObservationRegion("three", RegionalAssignmentPolicy.MARINE_WORLDWIDE, "v1", ObservationRegionAssignment.MARINE_WORLDWIDE)

        assertTrue(land.earnsRegionalProgress)
        assertTrue(offshore.earnsRegionalProgress)
        assertFalse(marine.earnsRegionalProgress)
    }

    @Test
    fun `unmatched marine coordinate uses worldwide context`() {
        assertEquals(
            ObservationRegionAssignment.MARINE_WORLDWIDE,
            RegionalAssignmentPolicy.assignmentFor(false, false, true, false),
        )
    }
}
