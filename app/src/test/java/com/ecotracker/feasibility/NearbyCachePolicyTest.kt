package com.wildlife.feasibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that decide when a cached Near me answer is replaced.
 *
 * Worth pinning down because every one of these branches costs either a network request the
 * user did not ask for, or a stale answer they cannot tell is stale.
 */
class NearbyCachePolicyTest {

    private val nowMs = 1_755_907_200_000L
    private val barcelona = 41.3874 to 2.1686

    private fun cached(
        latitude: Double = barcelona.first,
        longitude: Double = barcelona.second,
        radiusKm: Int = 25,
        month: Int = 8,
        fetchedAtMs: Long = nowMs - 60_000,
        species: List<NearbySpecies> = listOf(
            NearbySpecies(1, "European robin", "Erithacus rubecula", "birds", 412),
        ),
    ) = CachedNearbyDiscovery(latitude, longitude, radiusKm, month, fetchedAtMs, species)

    private fun isFresh(
        cache: CachedNearbyDiscovery,
        latitude: Double? = barcelona.first,
        longitude: Double? = barcelona.second,
        month: Int = 8,
        radiusKm: Int = 25,
        atMs: Long = nowMs,
    ) = NearbyCachePolicy.isFresh(cache, atMs, month, latitude, longitude, radiusKm)

    @Test
    fun `a recent answer at the same place is reused`() {
        assertTrue(isFresh(cached()))
    }

    @Test
    fun `moving beyond the threshold invalidates the answer`() {
        // ~11 km north: comfortably past the 5 km threshold.
        assertFalse(isFresh(cached(), latitude = barcelona.first + 0.1))
    }

    @Test
    fun `moving a short distance does not trigger a new search`() {
        // ~1.1 km north. Re-searching here would sample location constantly for an answer
        // that has not meaningfully changed.
        assertTrue(isFresh(cached(), latitude = barcelona.first + 0.01))
    }

    @Test
    fun `a new calendar month invalidates the answer`() {
        // The month is part of the query, so a September answer is not an August one.
        assertFalse(isFresh(cached(month = 8), month = 9))
    }

    @Test
    fun `an answer older than the maximum age is refetched`() {
        val stale = cached(fetchedAtMs = nowMs - NearbyCachePolicy.MAX_AGE_MS - 1)
        assertFalse(isFresh(stale))
    }

    @Test
    fun `a changed radius invalidates the answer`() {
        assertFalse(isFresh(cached(radiusKm = 25), radiusKm = 50))
    }

    @Test
    fun `a future timestamp is treated as invalid rather than fresh forever`() {
        // Clock changes and restored backups can both produce this; without the check a
        // single bad timestamp would pin the cache permanently.
        assertFalse(isFresh(cached(fetchedAtMs = nowMs + 60_000)))
    }

    @Test
    fun `without a coordinate the answer is judged on age and month alone`() {
        // Someone who never grants location again keeps their last answer rather than
        // being shown an empty section.
        assertTrue(isFresh(cached(), latitude = null, longitude = null))
        assertFalse(isFresh(cached(month = 7), latitude = null, longitude = null))
    }

    @Test
    fun `the stored coordinate is coarsened to the cell`() {
        // Nothing finer than ~1 km ever reaches disk.
        assertEquals(41.39, NearbyDiscoveryStore.coarsen(41.387_431_9), 1e-9)
        assertEquals(2.17, NearbyDiscoveryStore.coarsen(2.168_612_4), 1e-9)
        assertEquals(-0.12, NearbyDiscoveryStore.coarsen(-0.123_456), 1e-9)
    }

    @Test
    fun `coarsened coordinates within one cell compare equal`() {
        assertTrue(NearbyCachePolicy.sameCell(41.3874, 2.1686, 41.3891, 2.1712))
        assertFalse(NearbyCachePolicy.sameCell(41.3874, 2.1686, 41.4874, 2.1686))
    }

    @Test
    fun `distance is measured as a great circle`() {
        // One degree of latitude is ~111 km anywhere on the globe.
        assertEquals(111.2, NearbyCachePolicy.distanceKm(41.0, 2.0, 42.0, 2.0), 0.5)
        assertEquals(0.0, NearbyCachePolicy.distanceKm(41.0, 2.0, 41.0, 2.0), 1e-6)
    }
}
