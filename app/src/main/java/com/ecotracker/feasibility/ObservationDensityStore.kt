package com.wildlife.feasibility

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.math.floor
import org.json.JSONArray
import org.json.JSONObject

data class ObservationDensityCell(
    val latitude: Double,
    val longitude: Double,
    val count: Int,
)

data class ObservationDensitySnapshot(
    val taxonId: Long,
    val cells: List<ObservationDensityCell>,
    val sampledObservations: Int,
    val totalResults: Int,
    val fetchedAtMs: Long,
) {
    val isCappedSample: Boolean get() = totalResults > sampledObservations
}

data class ObservationDensityResult(
    val snapshot: ObservationDensitySnapshot?,
    val fromCache: Boolean,
    val stale: Boolean = false,
    val failureCode: String? = null,
)

/**
 * Bounded cache for coarse public iNaturalist observation density. Raw observations, users,
 * dates, IDs and coordinates are discarded before anything reaches disk.
 */
class ObservationDensityStore internal constructor(
    context: Context,
    private val http: ReadOnlyHttpClient = ReadOnlyHttpClient(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val root = File(context.applicationContext.filesDir, DIRECTORY).apply { mkdirs() }

    fun load(taxonId: Long, force: Boolean = false): ObservationDensityResult = synchronized(LOCK) {
        require(taxonId > 0) { "Observation density requires a positive taxon ID." }
        val cached = read(taxonId)
        val now = clock()
        if (!force && cached != null && now >= cached.fetchedAtMs && now - cached.fetchedAtMs <= MAX_AGE_MS) {
            touch(taxonId, now)
            return@synchronized ObservationDensityResult(cached, fromCache = true)
        }
        return@synchronized try {
            val fresh = fetch(taxonId, now)
            write(fresh)
            trim()
            ObservationDensityResult(fresh, fromCache = false)
        } catch (_: RemoteDataException) {
            ObservationDensityResult(cached, fromCache = cached != null, stale = cached != null, failureCode = "network_failure")
        } catch (_: Exception) {
            ObservationDensityResult(cached, fromCache = cached != null, stale = cached != null, failureCode = "invalid_response")
        }
    }

    fun clear() = synchronized(LOCK) {
        root.deleteRecursively()
        root.mkdirs()
    }

    private fun fetch(taxonId: Long, fetchedAtMs: Long): ObservationDensitySnapshot {
        val seen = hashSetOf<Long>()
        val counts = linkedMapOf<Pair<Int, Int>, Int>()
        var totalResults = 0
        var pages = 1
        for (page in 1..MAX_PAGES) {
            if (page > pages) break
            val response = http.getJson(
                urlWithParameters(
                    "$API/observations",
                    mapOf(
                        "taxon_id" to taxonId.toString(),
                        "geo" to "true",
                        "mappable" to "true",
                        "quality_grade" to "research",
                        "per_page" to PAGE_SIZE.toString(),
                        "page" to page.toString(),
                        "order_by" to "observed_on",
                        "order" to "desc",
                    ),
                ),
                REQUEST_POLICY,
            )
            if (page == 1) {
                totalResults = response.optInt("total_results", 0).coerceAtLeast(0)
                pages = minOf(MAX_PAGES, ceil(totalResults / PAGE_SIZE.toDouble()).toInt().coerceAtLeast(1))
            }
            val results = response.optJSONArray("results") ?: throw IllegalArgumentException("Missing results")
            for (index in 0 until results.length()) {
                val observation = results.optJSONObject(index) ?: continue
                val id = observation.optLong("id", -1L)
                if (id <= 0 || !seen.add(id)) continue
                val coordinates = observation.optJSONObject("geojson")?.optJSONArray("coordinates") ?: continue
                val longitude = coordinates.optDouble(0, Double.NaN)
                val latitude = coordinates.optDouble(1, Double.NaN)
                if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) continue
                val latitudeCell = floor(latitude).toInt().coerceIn(-90, 89)
                val longitudeCell = floor(longitude).toInt().coerceIn(-180, 179)
                val key = latitudeCell to longitudeCell
                counts[key] = (counts[key] ?: 0) + 1
            }
        }
        val cells = counts.entries.map { (key, count) ->
            ObservationDensityCell(key.first + 0.5, key.second + 0.5, count)
        }.sortedWith(compareByDescending<ObservationDensityCell> { it.count }.thenBy { it.latitude }.thenBy { it.longitude })
        return ObservationDensitySnapshot(taxonId, cells, seen.size, totalResults, fetchedAtMs)
    }

    private fun read(taxonId: Long): ObservationDensitySnapshot? {
        val file = file(taxonId)
        if (!file.isFile) return null
        return runCatching {
            val root = JSONObject(file.readText())
            require(root.getInt("schema_version") == SCHEMA_VERSION)
            require(root.getLong("taxon_id") == taxonId)
            val cellsJson = root.getJSONArray("cells")
            val cells = buildList {
                for (index in 0 until cellsJson.length()) {
                    val cell = cellsJson.getJSONObject(index)
                    val latitude = cell.getDouble("latitude")
                    val longitude = cell.getDouble("longitude")
                    val count = cell.getInt("count")
                    require(latitude in -90.0..90.0 && longitude in -180.0..180.0 && count > 0)
                    add(ObservationDensityCell(latitude, longitude, count))
                }
            }
            ObservationDensitySnapshot(
                taxonId, cells, root.getInt("sampled_observations"),
                root.getInt("total_results"), root.getLong("fetched_at_ms"),
            )
        }.getOrElse {
            file.delete()
            null
        }
    }

    private fun write(snapshot: ObservationDensitySnapshot) {
        val destination = file(snapshot.taxonId)
        val temporary = File(root, ".${destination.name}.${System.nanoTime()}.tmp")
        val document = JSONObject()
            .put("schema_version", SCHEMA_VERSION)
            .put("taxon_id", snapshot.taxonId)
            .put("sampled_observations", snapshot.sampledObservations)
            .put("total_results", snapshot.totalResults)
            .put("fetched_at_ms", snapshot.fetchedAtMs)
            .put("cells", JSONArray().apply {
                snapshot.cells.forEach { cell ->
                    put(JSONObject().put("latitude", cell.latitude).put("longitude", cell.longitude).put("count", cell.count))
                }
            })
        FileOutputStream(temporary).use { output ->
            output.write(document.toString().toByteArray())
            output.fd.sync()
        }
        check(temporary.renameTo(destination)) { "Observation density cache could not be committed." }
        destination.setLastModified(clock())
    }

    private fun trim() {
        root.listFiles().orEmpty().filter { it.name.startsWith("taxon-") && it.extension == "json" }
            .sortedByDescending(File::lastModified).drop(MAX_SNAPSHOTS).forEach(File::delete)
    }

    private fun touch(taxonId: Long, now: Long) { file(taxonId).setLastModified(now) }
    private fun file(taxonId: Long) = File(root, "taxon-$taxonId.json")

    private companion object {
        val LOCK = Any()
        const val API = "https://api.inaturalist.org/v1"
        const val DIRECTORY = "observation_density_v1"
        const val SCHEMA_VERSION = 1
        const val PAGE_SIZE = 50
        const val MAX_PAGES = 3
        const val MAX_SNAPSHOTS = 40
        const val MAX_AGE_MS = 30L * 24L * 60L * 60L * 1_000L
        val REQUEST_POLICY = ReadOnlyRequestPolicy(
            serviceName = "iNaturalist observation density",
            userAgent = WildlifeNetworkIdentity.BIOLOGICAL_DATA_USER_AGENT,
            rateLimitKey = "api.inaturalist.org",
            minimumIntervalMs = 1_100L,
            readTimeoutMs = 15_000,
            maxResponseBytes = 4L * 1024L * 1024L,
        )
    }
}
