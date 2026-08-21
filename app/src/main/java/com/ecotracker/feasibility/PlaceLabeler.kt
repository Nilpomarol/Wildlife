package com.wildlife.feasibility

import android.content.Context
import android.location.Geocoder
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * A coordinate pair to reverse-geocode into a short human place label.
 */
data class PlaceRequest(val latitude: Double, val longitude: Double)

/**
 * Stable, coarse cache keys for coordinates. Rounding to three decimals (~110m) keeps nearby
 * sightings sharing one lookup and one label, and keeps the key independent of float noise.
 */
object PlaceKeys {
    fun of(latitude: Double, longitude: Double): String =
        "%.3f,%.3f".format(latitude, longitude)
}

/**
 * Resolves coordinates into short place names (a locality or administrative area) using the
 * on-device [Geocoder]. No place name is stored on Wildlife records, so this fills the gap
 * lazily and off the main thread, caching every attempt so a coordinate is looked up once.
 *
 * Obscured public coordinates still resolve, but only to the coarse area iNaturalist exposes.
 */
class PlaceLabeler(context: Context) {
    private val appContext = context.applicationContext

    // key -> label. An empty string marks "attempted, nothing usable" so we never retry it.
    private val cache = ConcurrentHashMap<String, String>()

    @Volatile
    private var running = false

    /** Resolved labels only (attempted-but-empty entries are filtered out). */
    fun snapshot(): Map<String, String> = cache.filterValues { it.isNotEmpty() }

    /**
     * Geocodes any [requests] whose key is not yet cached, on a background thread. Invokes
     * [onResolved] with a fresh [snapshot] only when at least one new label was found. Safe to
     * call on every reload: cached keys are skipped, so this quiesces once everything is known.
     */
    fun ensureResolved(requests: List<PlaceRequest>, onResolved: (Map<String, String>) -> Unit) {
        if (running || !Geocoder.isPresent()) return
        val pending = requests
            .distinctBy { PlaceKeys.of(it.latitude, it.longitude) }
            .filter { !cache.containsKey(PlaceKeys.of(it.latitude, it.longitude)) }
        if (pending.isEmpty()) return
        running = true
        thread(name = "wildlife-geocode") {
            val geocoder = Geocoder(appContext)
            var changed = false
            pending.forEach { request ->
                val key = PlaceKeys.of(request.latitude, request.longitude)
                cache[key] = lookup(geocoder, request).also { if (it.isNotEmpty()) changed = true }
            }
            running = false
            if (changed) onResolved(snapshot())
        }
    }

    @Suppress("DEPRECATION")
    private fun lookup(geocoder: Geocoder, request: PlaceRequest): String = runCatching {
        val address = geocoder.getFromLocation(request.latitude, request.longitude, 1)?.firstOrNull()
            ?: return ""
        listOfNotNull(
            address.locality,
            address.subAdminArea,
            address.adminArea,
            address.countryName,
        ).firstOrNull().orEmpty()
    }.getOrDefault("")
}
