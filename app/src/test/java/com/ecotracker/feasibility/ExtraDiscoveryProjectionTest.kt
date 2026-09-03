package com.wildlife.feasibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtraDiscoveryProjectionTest {
    private val mediterranean = InstalledRegionalCatalogue("mediterranean", "Mediterranean Europe", "2026.1")
    private val eastAfrica = InstalledRegionalCatalogue("east_africa", "East Africa", "2026.2")

    @Test
    fun `confirmed species absent from assigned catalogue is extra`() {
        val result = ExtraDiscoveryProjection.contextsByTaxon(
            observations = listOf(observation("one", 42, confirmed = true)),
            regionsByObservationUuid = mapOf("one" to region("one", "mediterranean")),
            catalogues = listOf(mediterranean),
            catalogueTaxaByRegion = mapOf("mediterranean" to setOf(7L)),
        )

        assertEquals(
            listOf(ExtraDiscoveryContext("mediterranean", "Mediterranean Europe", "2026.1")),
            result.getValue(42L),
        )
    }

    @Test
    fun `catalogue member unconfirmed and coarse taxa are excluded`() {
        val observations = listOf(
            observation("member", 42, confirmed = true),
            observation("unconfirmed", 43, confirmed = false),
            observation("genus", 44, confirmed = true, rank = "genus"),
        )
        val assignments = observations.associate { it.uuid to region(it.uuid, "mediterranean") }

        val result = ExtraDiscoveryProjection.contextsByTaxon(
            observations, assignments, listOf(mediterranean),
            mapOf("mediterranean" to setOf(42L)),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `same species can be extra in one region and catalogued in another`() {
        val result = ExtraDiscoveryProjection.contextsByTaxon(
            observations = listOf(
                observation("med", 42, confirmed = true),
                observation("east", 42, confirmed = true),
            ),
            regionsByObservationUuid = mapOf(
                "med" to region("med", "mediterranean"),
                "east" to region("east", "east_africa"),
            ),
            catalogues = listOf(mediterranean, eastAfrica),
            catalogueTaxaByRegion = mapOf(
                "mediterranean" to emptySet(),
                "east_africa" to setOf(42L),
            ),
        )

        assertEquals(listOf("mediterranean"), result.getValue(42L).map { it.regionKey })
        assertFalse(result.getValue(42L).any { it.regionKey == "east_africa" })
    }

    @Test
    fun `uncertain assignments do not make regional extras`() {
        val observation = observation("one", 42, confirmed = true)
        val result = ExtraDiscoveryProjection.contextsByTaxon(
            listOf(observation),
            mapOf(
                "one" to ObservationRegion(
                    "one", "mediterranean", "boundaries-v1",
                    ObservationRegionAssignment.REGION_UNCERTAIN,
                ),
            ),
            listOf(mediterranean),
            mapOf("mediterranean" to emptySet()),
        )

        assertTrue(result.isEmpty())
    }

    private fun region(uuid: String, regionKey: String) = ObservationRegion(
        uuid, regionKey, "boundaries-v1", ObservationRegionAssignment.LAND_POLYGON,
    )

    private fun observation(
        uuid: String,
        taxonId: Long,
        confirmed: Boolean,
        rank: String = "species",
    ) = SyncedObservation(
        id = taxonId,
        uuid = uuid,
        taxonId = taxonId,
        taxonRank = rank,
        collectionTaxonId = taxonId,
        collectionTaxonRank = rank,
        label = "Test species",
        observedAtMs = taxonId,
        latitude = 1.0,
        longitude = 1.0,
        obscured = false,
        createdAtMs = taxonId,
        qualityGrade = "needs_id",
        photoUrl = null,
        confirmed = confirmed,
    )
}
