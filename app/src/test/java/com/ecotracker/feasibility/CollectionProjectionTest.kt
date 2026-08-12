package com.wildlife.feasibility

import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionProjectionTest {
    @Test
    fun groupsSubspeciesUnderParentAndKeepsGenusAsPendingEntry() {
        val observations = listOf(
            observation("one", taxonId = 42, collectionTaxonId = 42, collectionRank = "species", confirmed = false, observedAt = 100),
            observation("two", taxonId = 43, collectionTaxonId = 42, collectionRank = "species", confirmed = true, observedAt = 200),
            observation("three", taxonId = 99, collectionTaxonId = 99, collectionRank = "genus", confirmed = false, observedAt = 150),
        )

        val species = CollectionProjection.species(observations)

        assertEquals(2, species.size)
        val robin = species.single { it.taxonId == 42L }
        assertEquals(2, robin.observationCount)
        assertEquals(1, robin.rewardedObservationCount)
        assertEquals("two", robin.latestObservationUuid)
        assertEquals(false, robin.awaitingSpeciesIdentification)
        assertEquals(true, species.single { it.taxonId == 99L }.awaitingSpeciesIdentification)
    }

    @Test
    fun unidentifiedObservationsRemainSeparateCollectionEntries() {
        val observations = listOf(
            observation("one", taxonId = null, collectionTaxonId = null, collectionRank = null, confirmed = false, observedAt = 100),
            observation("two", taxonId = null, collectionTaxonId = null, collectionRank = null, confirmed = false, observedAt = 200),
        )

        assertEquals(2, CollectionProjection.species(observations).size)
    }

    private fun observation(
        uuid: String,
        taxonId: Long?,
        collectionTaxonId: Long?,
        collectionRank: String?,
        confirmed: Boolean,
        observedAt: Long,
    ) = SyncedObservation(
        id = observedAt,
        uuid = uuid,
        taxonId = taxonId,
        taxonRank = if (taxonId == collectionTaxonId) collectionRank else "subspecies",
        collectionTaxonId = collectionTaxonId,
        collectionTaxonRank = collectionRank,
        label = if (taxonId == 42L) "European robin" else "Other",
        observedAtMs = observedAt,
        latitude = null,
        longitude = null,
        obscured = false,
        createdAtMs = observedAt,
        qualityGrade = "needs_id",
        photoUrl = null,
        confirmed = confirmed,
    )
}
