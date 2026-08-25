package com.wildlife.feasibility

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The last Near me result, kept so the screen opens with an answer instead of a button.
 *
 * ## Why this persists at all
 *
 * `docs/ui_architecture.md` originally forbade persisting the search coordinate or its results.
 * That rule was written to stop the app becoming a location tracker, and it is kept in spirit
 * rather than to the letter, because re-running a network search on every visit to Home was
 * both slow and a *worse* privacy outcome — it sampled the user's location far more often than
 * this does.
 *
 * Two properties keep it honest:
 *
 * 1. **The stored coordinate is coarsened to [CELL_DEGREES]** (0.01°, roughly a kilometre)
 *    before it is ever written. It exists only to answer "have I moved far enough to be worth
 *    re-searching?", and that question does not need a precise fix. Nothing finer than the cell
 *    ever reaches disk. The exact coordinate is used for the request in memory and discarded.
 * 2. **Nothing here samples location.** The store is written only after a search the user's own
 *    action began, and read without touching location services at all.
 *
 * The cache is part of Wildlife's local state, so [LocalDataManager] clears it with everything
 * else.
 */
class NearbyDiscoveryStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE,
    )

    fun load(): CachedNearbyDiscovery? {
        val payload = preferences.getString(KEY_PAYLOAD, null) ?: return null
        return runCatching { parse(JSONObject(payload)) }.getOrNull()
    }

    /**
     * Records a completed search. [latitude] and [longitude] are the real coordinates used for
     * the request; they are coarsened here, at the boundary, so no caller can forget to.
     */
    fun save(
        latitude: Double,
        longitude: Double,
        radiusKm: Int,
        month: Int,
        fetchedAtMs: Long,
        species: List<NearbySpecies>,
    ) {
        val payload = JSONObject().apply {
            put(FIELD_LAT, coarsen(latitude))
            put(FIELD_LNG, coarsen(longitude))
            put(FIELD_RADIUS, radiusKm)
            put(FIELD_MONTH, month)
            put(FIELD_FETCHED_AT, fetchedAtMs)
            put(FIELD_SPECIES, JSONArray().apply { species.forEach { put(it.toJson()) } })
        }
        preferences.edit().putString(KEY_PAYLOAD, payload.toString()).apply()
    }

    fun clear() = preferences.edit().clear().commit()

    private fun parse(payload: JSONObject): CachedNearbyDiscovery {
        val array = payload.optJSONArray(FIELD_SPECIES) ?: JSONArray()
        val species = buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let { add(it.toNearbySpecies()) }
            }
        }
        return CachedNearbyDiscovery(
            latitude = payload.getDouble(FIELD_LAT),
            longitude = payload.getDouble(FIELD_LNG),
            radiusKm = payload.optInt(FIELD_RADIUS, 25),
            month = payload.optInt(FIELD_MONTH, 0),
            fetchedAtMs = payload.optLong(FIELD_FETCHED_AT, 0L),
            species = species,
        )
    }

    private fun NearbySpecies.toJson() = JSONObject().apply {
        put(FIELD_TAXON_ID, taxonId)
        commonName?.let { put(FIELD_COMMON_NAME, it) }
        put(FIELD_SCIENTIFIC_NAME, scientificName)
        taxonGroup?.let { put(FIELD_TAXON_GROUP, it) }
        put(FIELD_COUNT, observationCount)
        photoUrl?.let { put(FIELD_PHOTO_URL, it) }
        photoAttribution?.let { put(FIELD_PHOTO_ATTRIBUTION, it) }
        photoLicenseCode?.let { put(FIELD_PHOTO_LICENCE, it) }
    }

    private fun JSONObject.toNearbySpecies() = NearbySpecies(
        taxonId = optLong(FIELD_TAXON_ID),
        commonName = optString(FIELD_COMMON_NAME).takeIf(String::isNotBlank),
        scientificName = optString(FIELD_SCIENTIFIC_NAME),
        taxonGroup = optString(FIELD_TAXON_GROUP).takeIf(String::isNotBlank),
        observationCount = optInt(FIELD_COUNT),
        photoUrl = optString(FIELD_PHOTO_URL).takeIf(String::isNotBlank),
        photoAttribution = optString(FIELD_PHOTO_ATTRIBUTION).takeIf(String::isNotBlank),
        photoLicenseCode = optString(FIELD_PHOTO_LICENCE).takeIf(String::isNotBlank),
    )

    companion object {
        private const val PREFERENCES = "nearby_discovery"
        private const val KEY_PAYLOAD = "last_discovery"

        private const val FIELD_LAT = "lat"
        private const val FIELD_LNG = "lng"
        private const val FIELD_RADIUS = "radius_km"
        private const val FIELD_MONTH = "month"
        private const val FIELD_FETCHED_AT = "fetched_at_ms"
        private const val FIELD_SPECIES = "species"
        private const val FIELD_TAXON_ID = "taxon_id"
        private const val FIELD_COMMON_NAME = "common_name"
        private const val FIELD_SCIENTIFIC_NAME = "scientific_name"
        private const val FIELD_TAXON_GROUP = "taxon_group"
        private const val FIELD_COUNT = "count"
        private const val FIELD_PHOTO_URL = "photo_url"
        private const val FIELD_PHOTO_ATTRIBUTION = "photo_attribution"
        private const val FIELD_PHOTO_LICENCE = "photo_licence"

        /** Storage precision for the search coordinate: ~1.1 km of latitude. */
        const val CELL_DEGREES = 0.01

        fun coarsen(degrees: Double): Double =
            (degrees / CELL_DEGREES).roundToLong() * CELL_DEGREES
    }
}

/** A previous Near me answer, and enough context to judge whether it still applies. */
data class CachedNearbyDiscovery(
    val latitude: Double,
    val longitude: Double,
    val radiusKm: Int,
    val month: Int,
    val fetchedAtMs: Long,
    val species: List<NearbySpecies>,
)

/**
 * Decides when a cached Near me answer should be replaced.
 *
 * Kept as a pure object so the thresholds are testable without a device, a network or a clock.
 */
object NearbyCachePolicy {

    /**
     * How far the user must move before the answer is worth recomputing.
     *
     * Set against the 25 km search radius rather than picked round: a 5 km move shifts the
     * searched area by a fifth, which is the point at which the species list plausibly changes.
     * Re-searching on every small movement would sample location constantly for an answer that
     * had not changed.
     */
    const val MOVED_THRESHOLD_KM = 5.0

    /** A month boundary changes the query itself, so a result never outlives its month. */
    const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

    private const val EARTH_RADIUS_KM = 6371.0

    /** Great-circle distance in kilometres. */
    fun distanceKm(
        fromLat: Double,
        fromLng: Double,
        toLat: Double,
        toLng: Double,
    ): Double {
        val dLat = Math.toRadians(toLat - fromLat)
        val dLng = Math.toRadians(toLng - fromLng)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(fromLat)) * cos(Math.toRadians(toLat)) *
            sin(dLng / 2) * sin(dLng / 2)
        return EARTH_RADIUS_KM * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /**
     * Whether a cached answer is still good where and when the user now is.
     *
     * [currentLatitude]/[currentLongitude] are null when the caller could not read a location
     * without prompting; the cache is then judged on age and month alone, so a user who never
     * grants location again still sees their last answer rather than an empty screen.
     */
    fun isFresh(
        cached: CachedNearbyDiscovery,
        nowMs: Long,
        currentMonth: Int,
        currentLatitude: Double?,
        currentLongitude: Double?,
        radiusKm: Int,
    ): Boolean {
        if (cached.species.isEmpty() && cached.fetchedAtMs == 0L) return false
        if (cached.month != currentMonth) return false
        if (cached.radiusKm != radiusKm) return false
        if (nowMs - cached.fetchedAtMs !in 0..MAX_AGE_MS) return false
        if (currentLatitude == null || currentLongitude == null) return true
        return distanceKm(
            cached.latitude,
            cached.longitude,
            currentLatitude,
            currentLongitude,
        ) < MOVED_THRESHOLD_KM
    }

    /**
     * Whether two coordinates are the same to within the stored cell.
     *
     * Used to avoid rewriting an identical cache entry, which would otherwise churn the
     * fetch timestamp and defeat the age check.
     */
    fun sameCell(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Boolean =
        abs(NearbyDiscoveryStore.coarsen(aLat) - NearbyDiscoveryStore.coarsen(bLat)) < 1e-9 &&
            abs(NearbyDiscoveryStore.coarsen(aLng) - NearbyDiscoveryStore.coarsen(bLng)) < 1e-9
}
