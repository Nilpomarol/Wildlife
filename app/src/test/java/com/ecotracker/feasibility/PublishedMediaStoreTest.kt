package com.wildlife.feasibility

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
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
class PublishedMediaStoreTest {
    private lateinit var context: Context
    private val jpegA = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2, 3)
    private val jpegB = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 4, 5, 6)

    @Before fun clear() {
        context = ApplicationProvider.getApplicationContext()
        LocalMediaIndexes.resetForTests()
        LocalMediaStore(context).clear()
    }

    @After fun closeIndex() { LocalMediaIndexes.resetForTests() }

    @Test fun `validated download is deduplicated and corruption is removed`() {
        var requests = 0
        val store = store(64) { requests++; jpegA }
        val asset = asset("one", jpegA)
        val first = store.downloadNow("g1", asset, "thumbnail")
        val second = store.downloadNow("g1", asset, "thumbnail")
        assertNotNull(first.localUri)
        assertEquals(true, second.fromCache)
        assertEquals(1, requests)

        java.io.File(android.net.Uri.parse(first.localUri).path!!).apply {
            val previousModifiedAt = lastModified()
            writeBytes(jpegB)
            setLastModified(previousModifiedAt + 1_000)
        }
        assertNull(store.bestLocalVariant(asset, "thumbnail"))
        assertEquals(0, store.inventory().fileCount)
    }

    @Test fun `checksum failure never becomes readable`() {
        val store = store(64) { jpegB }
        val result = store.downloadNow("g1", asset("one", jpegA), "thumbnail")
        assertEquals("validation_failed", result.failureCode)
        assertNull(result.localUri)
        assertEquals(0, store.inventory().fileCount)
    }

    @Test fun `lru evicts unpinned files and generation reconciliation removes orphans`() {
        var response = jpegA
        var now = 1L
        val store = LocalMediaStore(context, 12, { FakeConnection(it, response) }, { now++ })
        store.downloadNow("g1", asset("one", jpegA), "thumbnail", pinned = true)
        response = jpegB
        store.downloadNow("g1", asset("two", jpegB), "thumbnail")
        response = jpegA
        store.downloadNow("g1", asset("three", jpegA), "thumbnail")
        assertNotNull(store.bestLocalVariant(asset("one", jpegA), "thumbnail"))
        assertNull(store.bestLocalVariant(asset("two", jpegB), "thumbnail"))
        assertNotNull(store.bestLocalVariant(asset("three", jpegA), "thumbnail"))

        assertEquals(1, store.reconcileGeneration("g2", setOf("one")))
        assertEquals(1, store.inventory().fileCount)
    }

    @Test fun `legacy JSON backup is migrated transactionally without redownloading`() {
        LocalMediaIndexes.resetForTests()
        context.deleteDatabase("reference-media-index.db")
        val root = java.io.File(context.filesDir, "reference_media_v2").apply {
            deleteRecursively()
            mkdirs()
        }
        java.io.File(root, "legacy.jpg").writeBytes(jpegA)
        val hash = MessageDigest.getInstance("SHA-256").digest(jpegA).joinToString("") { "%02x".format(it) }
        val legacyJson = JSONObject().put("schema_version", 1).put(
                "entries",
                JSONArray().put(
                    JSONObject()
                        .put("asset_id", "one")
                        .put("variant", "thumbnail")
                        .put("file_name", "legacy.jpg")
                        .put("bytes", jpegA.size)
                        .put("sha256", hash)
                        .put("mime_type", "image/jpeg")
                        .put("generation_id", "g1")
                        .put("pinned", false)
                        .put("downloaded_at", 1)
                        .put("last_accessed_at", 1),
                ),
            ).toString()
        java.io.File(root, "media-index.json").writeText("{damaged")
        java.io.File(root, "media-index.json.backup").writeText(legacyJson)

        val store = LocalMediaStore(context, 64, { error("migration must not download") })
        assertNotNull(store.bestLocalVariant(asset("one", jpegA), "thumbnail"))
        assertEquals(StoredMediaSummary(1, jpegA.size.toLong(), 64, 0), store.inventory())
        assertFalse(java.io.File(root, "media-index.json").exists())
        assertFalse(java.io.File(root, "media-index.json.backup").exists())
    }

    @Test fun `generation reconciliation preserves runtime media repairs`() {
        val store = store(64) { jpegA }
        val repaired = asset("legacy-photo:42", jpegA)

        assertNotNull(store.downloadNow("legacy", repaired, "thumbnail").localUri)
        assertEquals(0, store.reconcileGeneration("g2", emptySet()))
        assertNotNull(store.bestLocalVariant(repaired, "thumbnail"))
    }

    @Test fun `successive runtime photo choices keep distinct validated files`() {
        var requests = 0
        val store = LocalMediaStore(context, 64, { url ->
            requests++
            FakeConnection(url, if (url.path.endsWith("first.jpg")) jpegA else jpegB)
        })

        val first = store.storePhoto("https://example.test/first.jpg", 42)
        val second = store.storePhoto("https://example.test/second.jpg", 42)

        assertNotNull(first)
        assertNotNull(second)
        assertFalse(first == second)
        assertEquals(2, requests)
    }

    @Test fun `slow download does not block unrelated cached lookup`() {
        val cachedStore = store(64) { jpegA }
        assertNotNull(cachedStore.downloadNow("g1", asset("one", jpegA), "thumbnail").localUri)
        val enteredDownload = CountDownLatch(1)
        val releaseDownload = CountDownLatch(1)
        val slowStore = LocalMediaStore(context, 64, { BlockingConnection(it, jpegB, enteredDownload, releaseDownload) })
        val pool = Executors.newFixedThreadPool(2)
        try {
            val download = pool.submit(Callable { slowStore.downloadNow("g1", asset("two", jpegB), "thumbnail") })
            assertTrue(enteredDownload.await(1, TimeUnit.SECONDS))
            val lookup = pool.submit(Callable { cachedStore.bestLocalVariant(asset("one", jpegA), "thumbnail") })
            assertNotNull(lookup.get(1, TimeUnit.SECONDS))
            releaseDownload.countDown()
            assertNotNull(download.get(1, TimeUnit.SECONDS).localUri)
        } finally {
            releaseDownload.countDown()
            pool.shutdownNow()
        }
    }

    private fun store(capacity: Long, response: () -> ByteArray) =
        LocalMediaStore(context, capacity, { FakeConnection(it, response()) })

    private fun asset(id: String, bytes: ByteArray): PublishedMediaAsset {
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return PublishedMediaAsset(id, "photo", "test", id, "https://example.test/source", "Author", "cc0", null, "species", "Test species", listOf(
            PublishedMediaVariant("thumbnail", "https://example.test/$id.jpg", "image/jpeg", 1, 1, bytes.size.toLong(), hash),
        ))
    }

    private class FakeConnection(url: URL, private val bytes: ByteArray) : HttpURLConnection(url) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode() = 200
        override fun getContentType() = "image/jpeg"
        override fun getInputStream() = ByteArrayInputStream(bytes)
    }

    private class BlockingConnection(
        url: URL,
        private val bytes: ByteArray,
        private val entered: CountDownLatch,
        private val release: CountDownLatch,
    ) : HttpURLConnection(url) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode() = 200
        override fun getContentType() = "image/jpeg"
        override fun getInputStream(): ByteArrayInputStream {
            entered.countDown()
            check(release.await(2, TimeUnit.SECONDS))
            return ByteArrayInputStream(bytes)
        }
    }
}
