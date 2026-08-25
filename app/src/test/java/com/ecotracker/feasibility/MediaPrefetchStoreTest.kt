package com.wildlife.feasibility

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaPrefetchStoreTest {
    private lateinit var context: Context
    private lateinit var store: MediaPrefetchStore

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = MediaPrefetchStore(context)
        store.clear()
    }

    @After fun tearDown() { store.clear(); store.close() }

    @Test fun `enqueue is idempotent and upgrades priority`() {
        assertTrue(store.enqueue(listOf(job("a", 50)), 1))
        assertTrue(store.enqueue(listOf(job("a", 20)), 2))
        assertEquals(1, store.summary("g1").queued)
        assertEquals(20, store.claimNext("g1", 3)!!.priority)
    }

    @Test fun `open detail hero outranks every background media class`() {
        store.enqueue(
            listOf(
                job("region", MediaPrefetchStore.PRIORITY_REGION),
                job("achievement", MediaPrefetchStore.PRIORITY_ACHIEVEMENT),
                job("detail", MediaPrefetchStore.PRIORITY_DETAIL),
            ),
            1,
        )

        assertEquals("detail", store.claimNext("g1", 2)!!.assetId)
    }

    @Test fun `completed work is a true no-op when routinely enqueued again`() {
        assertTrue(store.enqueue(listOf(job("done", 20)), 1))
        val claimed = store.claimNext("g1", 2)!!
        store.complete(claimed, 3)

        assertFalse(store.enqueue(listOf(job("done", 10)), 4))
        assertEquals(MediaPrefetchSummary(0, 0, 1, 0), store.summary("g1"))
        assertNull(store.claimNext("g1", Long.MAX_VALUE))
    }

    @Test fun `retry delay does not starve a later ready asset`() {
        store.enqueue(listOf(job("first", 10), job("second", 20)), 1)
        val first = store.claimNext("g1", 2)!!
        store.fail(first, "network_failure", retryable = true, nowMs = 2)

        assertEquals("second", store.claimNext("g1", 3)!!.assetId)
        assertNull(store.claimNext("g1", 3))
        assertEquals("first", store.claimNext("g1", 30_002)!!.assetId)
    }

    @Test fun `process restart preserves a live claim then recovers it at lease expiry`() {
        store.enqueue(listOf(job("a", 20)), 1)
        assertNotNull(store.claimNext("g1", 2))
        store.close()

        store = MediaPrefetchStore(context)
        assertNull(store.claimNext("g1", 3))
        assertEquals(900_002L, store.nextDueAtMs("g1", 3))
        assertNotNull(store.claimNext("g1", 900_002L))
        store.reconcileGeneration("g2")
        assertEquals(MediaPrefetchSummary(0, 0, 0, 0), store.summary("g1"))
    }

    @Test fun `queue exposes exact retry time and released claims are immediately ready`() {
        store.enqueue(listOf(job("a", 20)), 1)
        val claimed = store.claimNext("g1", 2)!!
        store.fail(claimed, "network_failure", retryable = true, nowMs = 2)
        assertEquals(30_002L, store.nextDueAtMs("g1", 3))

        val retried = store.claimNext("g1", 30_002L)!!
        store.release(retried, 40_000L)
        assertEquals(40_000L, store.nextDueAtMs("g1", 40_000L))
        assertEquals("a", store.claimNext("g1", 40_000L)!!.assetId)
    }

    @Test fun `region switch deprioritizes obsolete work without deleting it`() {
        store.enqueue(listOf(job("old", 20)), 1)
        store.activateRegion("g1", "new-region")
        store.enqueue(listOf(job("new", 50).copy(regionKey = "new-region")), 2)

        assertEquals("new", store.claimNext("g1", 3)!!.assetId)
        assertEquals("old", store.claimNext("g1", 3)!!.assetId)
    }

    @Test fun `permanent failure is not silently retried by routine enqueue`() {
        store.enqueue(listOf(job("bad", 20)), 1)
        store.fail(store.claimNext("g1", 2)!!, "http_404", retryable = false, nowMs = 2)
        assertFalse(store.enqueue(listOf(job("bad", 20)), 3))

        assertEquals(1, store.summary("g1").failed)
        assertNull(store.claimNext("g1", Long.MAX_VALUE))
    }

    @Test fun `diagnostics expose detail priority retry time and failure codes without asset identity`() {
        store.enqueue(
            listOf(
                job("private-asset-42", MediaPrefetchStore.PRIORITY_DETAIL),
                job("retry", MediaPrefetchStore.PRIORITY_REGION),
                job("bad", MediaPrefetchStore.PRIORITY_REGION),
            ),
            1,
        )
        val detail = store.claimNext("g1", 2)!!
        val retry = store.claimNext("g1", 2)!!
        store.fail(retry, "network_failure", retryable = true, nowMs = 2)
        val bad = store.claimNext("g1", 2)!!
        store.fail(bad, "http_404", retryable = false, nowMs = 2)

        val diagnostics = store.diagnostics("g1", 3)
        assertEquals(1, diagnostics.detailRunning)
        assertEquals(0, diagnostics.detailQueued)
        assertEquals(30_002L, diagnostics.nextRetryAtMs)
        assertEquals(mapOf("http_404" to 1, "network_failure" to 1), diagnostics.failureCodes)
        assertFalse(diagnostics.toString().contains(detail.assetId))
    }

    private fun job(asset: String, priority: Int) = MediaPrefetchJob(
        "g1", "region", asset, "thumbnail", priority, 0, 0,
    )
}
