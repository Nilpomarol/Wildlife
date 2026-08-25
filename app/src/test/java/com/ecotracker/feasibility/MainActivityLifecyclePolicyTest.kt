package com.wildlife.feasibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityLifecyclePolicyTest {
    @Test
    fun `species detail return suppresses exactly one resume projection refresh`() {
        val gate = ResumeProjectionRefreshGate()

        gate.suppressNextRefresh()

        assertFalse(gate.consumeShouldRefresh())
        assertTrue(gate.consumeShouldRefresh())
    }

    @Test
    fun `cached unchanged sync does not rebuild the active projection`() {
        val result = syncResult(cached = true, observation = observation(confirmed = true))

        assertFalse(
            ForegroundProjectionRefreshPolicy.shouldRefresh(result, setOf("observation-1")),
        )
    }

    @Test
    fun `sync refreshes after remote work or a newly confirmed marker`() {
        assertTrue(
            ForegroundProjectionRefreshPolicy.shouldRefresh(
                syncResult(cached = false, observation = observation(confirmed = true)),
                emptySet(),
            ),
        )
        assertTrue(
            ForegroundProjectionRefreshPolicy.shouldRefresh(
                syncResult(cached = true, observation = observation(confirmed = false)),
                setOf("observation-1"),
            ),
        )
    }

    private fun syncResult(cached: Boolean, observation: SyncedObservation) =
        ObservationSyncResult(listOf(observation), CollectionSummary(), cached)

    private fun observation(confirmed: Boolean) = SyncedObservation(
        id = 1,
        uuid = "observation-1",
        taxonId = 10,
        taxonRank = "species",
        collectionTaxonId = 10,
        collectionTaxonRank = "species",
        label = "Example",
        observedAtMs = 1_000L,
        latitude = null,
        longitude = null,
        obscured = false,
        createdAtMs = 1_000L,
        qualityGrade = "research",
        photoUrl = null,
        confirmed = confirmed,
    )
}
