package com.wildlife.feasibility

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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

/** High-volume and failure-path checks for the durable on-device media pipeline. */
@RunWith(RobolectricTestRunner::class)
class MediaPipelineStressTest {
    private lateinit var context: Context
    private val jpegA = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2, 3)
    private val jpegB = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 4, 5, 6)

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        LocalMediaIndexes.resetForTests()
        LocalMediaStore(context).clear()
        MediaPrefetchStore(context).use { it.clear() }
    }

    @After fun tearDown() {
        MediaPrefetchStore(context).use { it.clear() }
        LocalMediaStore(context).clear()
        LocalMediaIndexes.resetForTests()
    }

    @Test fun `interrupted response leaves no partial file or reservation and retry succeeds`() {
        val interrupted = LocalMediaStore(context, 64, { FailingBodyConnection(it, jpegA) })
        val first = interrupted.downloadNow("g1", asset("one", jpegA), "thumbnail")

        assertEquals("network_failure", first.failureCode)
        assertEquals(0, interrupted.inventory().fileCount)
        assertFalse(mediaRoot().listFiles().orEmpty().any { it.name.endsWith(".part") })

        val recovered = LocalMediaStore(context, 64, { SuccessfulConnection(it, jpegA) })
            .downloadNow("g1", asset("one", jpegA), "thumbnail")
        assertNotNull(recovered.localUri)
    }

    @Test fun `full storage never evicts pinned media and reports a stable failure`() {
        var response = jpegA
        val store = LocalMediaStore(context, jpegA.size.toLong(), { SuccessfulConnection(it, response) })
        val pinned = asset("pinned", jpegA)
        assertNotNull(store.downloadNow("g1", pinned, "thumbnail", pinned = true).localUri)

        response = jpegB
        val rejected = store.downloadNow("g1", asset("new", jpegB), "thumbnail")
        assertEquals("storage_full", rejected.failureCode)
        assertNotNull(store.bestLocalVariant(pinned, "thumbnail"))
        assertEquals(1, store.inventory().fileCount)
        assertEquals(1, store.inventory().pinnedFiles)
    }

    @Test fun `concurrent requests for one variant perform exactly one download`() {
        val requests = AtomicInteger()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val store = LocalMediaStore(context, 64, { url ->
            requests.incrementAndGet()
            BlockingBodyConnection(url, jpegA, entered, release)
        })
        val pool = Executors.newFixedThreadPool(2)
        try {
            val first = pool.submit(Callable { store.downloadNow("g1", asset("shared", jpegA), "thumbnail") })
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            val second = pool.submit(Callable { store.downloadNow("g1", asset("shared", jpegA), "thumbnail") })
            release.countDown()

            assertNotNull(first.get(2, TimeUnit.SECONDS).localUri)
            assertTrue(second.get(2, TimeUnit.SECONDS).fromCache)
            assertEquals(1, requests.get())
            assertEquals(1, store.inventory().fileCount)
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }

    @Test fun `hundreds of retryable failures do not duplicate or starve ready work`() {
        MediaPrefetchStore(context).use { queue ->
            val jobs = (1..500).map { index ->
                MediaPrefetchJob("g1", "region", "asset-$index", "thumbnail", 50, 0, 0)
            }
            assertTrue(queue.enqueue(jobs, 1))
            val delayed = mutableSetOf<String>()
            while (true) {
                val job = queue.claimNext("g1", 2) ?: break
                if (job.assetId.substringAfterLast('-').toInt() % 5 == 0) {
                    delayed += job.assetId
                    queue.fail(job, "network_failure", retryable = true, nowMs = 2)
                } else {
                    queue.complete(job, 2)
                }
            }
            assertEquals(MediaPrefetchSummary(100, 0, 400, 0), queue.summary("g1"))

            val retried = mutableSetOf<String>()
            while (true) {
                val job = queue.claimNext("g1", 30_002) ?: break
                retried += job.assetId
                queue.complete(job, 30_002)
            }
            assertEquals(delayed, retried)
            assertEquals(MediaPrefetchSummary(0, 0, 500, 0), queue.summary("g1"))
            assertNull(queue.claimNext("g1", Long.MAX_VALUE))
        }
    }

    private fun mediaRoot() = java.io.File(context.filesDir, "reference_media_v2")

    private fun asset(id: String, bytes: ByteArray): PublishedMediaAsset {
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return PublishedMediaAsset(
            id, "photo", "test", id, "https://example.test/source/$id", "Test author", "cc0", null,
            "species", "Test species", listOf(
                PublishedMediaVariant(
                    "thumbnail", "https://example.test/$id.jpg", "image/jpeg", 1, 1,
                    bytes.size.toLong(), hash,
                ),
            ),
        )
    }

    private open class SuccessfulConnection(url: URL, protected val bytes: ByteArray) : HttpURLConnection(url) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode() = 200
        override fun getContentType() = "image/jpeg"
        override fun getInputStream(): InputStream = ByteArrayInputStream(bytes)
    }

    private class FailingBodyConnection(url: URL, bytes: ByteArray) : SuccessfulConnection(url, bytes) {
        override fun getInputStream(): InputStream = object : InputStream() {
            private var position = 0
            override fun read(): Int {
                if (position >= 3) throw IOException("connection interrupted")
                return bytes[position++].toInt() and 0xFF
            }
        }
    }

    private class BlockingBodyConnection(
        url: URL,
        bytes: ByteArray,
        private val entered: CountDownLatch,
        private val release: CountDownLatch,
    ) : SuccessfulConnection(url, bytes) {
        override fun getInputStream(): InputStream {
            entered.countDown()
            check(release.await(2, TimeUnit.SECONDS))
            return super.getInputStream()
        }
    }
}
