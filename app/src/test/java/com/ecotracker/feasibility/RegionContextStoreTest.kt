package com.wildlife.feasibility

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RegionContextStoreTest {
    private lateinit var store: RegionContextStore

    @Before fun setUp() {
        store = RegionContextStore(ApplicationProvider.getApplicationContext<Context>())
        store.clear()
    }

    @After fun tearDown() { store.clear() }

    @Test fun `browsing another guide does not change current region`() {
        val installed = setOf("mediterranean_europe", "east_africa", "caribbean")
        val current = store.recordLocation(
            latitude = 41.3874,
            longitude = 2.1686,
            locatedAtMs = 100,
            isCurrentFix = true,
            installedRegionKeys = installed,
        )

        store.selectBrowsedRegion("east_africa")

        assertEquals("mediterranean_europe", current.regionKey)
        assertEquals("mediterranean_europe", store.currentRegionKey(installed))
        assertEquals("east_africa", store.browsedRegionKey(installed.toList()))
    }

    @Test fun `fresh app launch returns browsing to current region`() {
        val installed = setOf("mediterranean_europe", "east_africa", "caribbean")
        store.recordLocation(41.3874, 2.1686, 100, true, installed)
        store.selectBrowsedRegion("east_africa")

        store.followCurrentRegion()

        assertNull(store.explicitBrowsedRegionKey())
        assertEquals("mediterranean_europe", store.browsedRegionKey(installed.toList()))
    }

    @Test fun `unsupported location does not silently choose the first guide`() {
        val installed = setOf("mediterranean_europe", "east_africa", "caribbean")
        val current = store.recordLocation(
            latitude = 35.6762,
            longitude = 139.6503,
            locatedAtMs = 100,
            isCurrentFix = true,
            installedRegionKeys = installed,
        )

        assertEquals(CurrentRegionSource.UNAVAILABLE, current.source)
        assertNull(store.currentRegionKey(installed))
    }

    @Test fun `a prior current fix becomes explicitly last known when no fresh fix exists`() {
        val installed = setOf("mediterranean_europe")
        store.recordLocation(41.3874, 2.1686, 100, true, installed)

        store.markCurrentAsLastKnown()

        assertEquals(CurrentRegionSource.LAST_KNOWN_FIX, store.currentRegion().source)
        assertEquals("mediterranean_europe", store.currentRegionKey(installed))
    }
}
