package com.wildlife.feasibility

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ObservationDensityStoreTest {
    private lateinit var context: Context

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ObservationDensityStore(context).clear()
    }

    @After fun tearDown() { ObservationDensityStore(context).clear() }

    @Test fun `raw observations are immediately reduced to coarse anonymous cells and cached`() {
        val requests = AtomicInteger()
        var requestedUrl: URL? = null
        val response = page(
            total = 3,
            observations = listOf(
                observation(1, 41.3874, 2.1686),
                observation(2, 41.901, 2.91),
                observation(3, -1.2, -78.6),
            ),
        )
        var now = 1_000L
        val store = ObservationDensityStore(
            context,
            ReadOnlyHttpClient({ url ->
                requests.incrementAndGet()
                requestedUrl = url
                JsonConnection(url, response)
            }),
            { now },
        )
        val first = store.load(42)
        assertFalse(first.fromCache)
        assertEquals(3, first.snapshot!!.sampledObservations)
        assertEquals(
            listOf(
                ObservationDensityCell(41.5, 2.5, 2),
                ObservationDensityCell(-1.5, -78.5, 1),
            ),
            first.snapshot.cells,
        )

        now += 1_000
        val cached = store.load(42)
        assertTrue(cached.fromCache)
        assertEquals(1, requests.get())
        assertTrue(requestedUrl.toString().contains("per_page=50"))
        val persisted = java.io.File(context.filesDir, "observation_density_v1/taxon-42.json").readText()
        assertFalse(persisted.contains("observer"))
        assertFalse(persisted.contains("41.3874"))
    }

    @Test fun `the density request names only the two fields it reads`() {
        var requestedUrl: URL? = null
        val store = ObservationDensityStore(
            context,
            ReadOnlyHttpClient({ url ->
                requestedUrl = url
                JsonConnection(url, page(1, listOf(observation(1, 10.1, 20.1))))
            }),
            { 1_000L },
        )

        store.load(42)

        val url = requestedUrl.toString()
        // v2, because v1 has no field selection and returned the whole observation graph:
        // 2.0-3.7 MiB per page against this store's 4 MiB ceiling, for two values.
        assertTrue(url, url.startsWith("https://api.inaturalist.org/v2/observations"))
        val fields = java.net.URLDecoder.decode(
            URL(url).query.split("&").first { it.startsWith("fields=") }.removePrefix("fields="),
            "UTF-8",
        )
        assertEquals("id,geojson.coordinates", fields)
    }

    @Test fun `network failure preserves a stale snapshot`() {
        var now = 1_000L
        val successful = ObservationDensityStore(
            context,
            ReadOnlyHttpClient({ url -> JsonConnection(url, page(1, listOf(observation(1, 10.1, 20.1)))) }),
            { now },
        )
        successful.load(42)
        now += 31L * 24L * 60L * 60L * 1_000L
        val offline = ObservationDensityStore(
            context,
            ReadOnlyHttpClient({ url -> FailingConnection(url) }, sleeper = {}),
            { now },
        ).load(42)
        assertTrue(offline.fromCache)
        assertTrue(offline.stale)
        assertEquals("network_failure", offline.failureCode)
        assertEquals(1, offline.snapshot!!.sampledObservations)
    }

    private fun observation(id: Long, latitude: Double, longitude: Double) = JSONObject()
        .put("id", id)
        .put("observer", "must never persist")
        .put("geojson", JSONObject().put("coordinates", JSONArray().put(longitude).put(latitude)))

    private fun page(total: Int, observations: List<JSONObject>) = JSONObject()
        .put("total_results", total)
        .put("results", JSONArray().apply { observations.forEach(::put) })

    private class JsonConnection(url: URL, private val response: JSONObject) : HttpURLConnection(url) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode() = 200
        override fun getInputStream() = ByteArrayInputStream(response.toString().toByteArray())
    }

    private class FailingConnection(url: URL) : HttpURLConnection(url) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode(): Int = throw IOException("offline")
    }
}
