package com.wildlife.feasibility

import android.content.Context

enum class CurrentRegionSource { CURRENT_FIX, LAST_KNOWN_FIX, UNAVAILABLE }

data class CurrentRegionContext(
    val regionKey: String?,
    val source: CurrentRegionSource,
    val locatedAtMs: Long?,
)

/**
 * Keeps physical location context separate from the guide being browsed.
 *
 * The current region can only be written from a boundary-assigned device location. Explore may
 * remember a browsed region, but that choice never changes regional progress or background work.
 */
class RegionContextStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun currentRegion(): CurrentRegionContext {
        val source = preferences.getString(KEY_CURRENT_SOURCE, null)
            ?.let { runCatching { CurrentRegionSource.valueOf(it) }.getOrNull() }
            ?: CurrentRegionSource.UNAVAILABLE
        return CurrentRegionContext(
            regionKey = preferences.getString(KEY_CURRENT_REGION, null)
                .takeIf { source != CurrentRegionSource.UNAVAILABLE },
            source = source,
            locatedAtMs = preferences.getLong(KEY_LOCATED_AT, -1L).takeIf { it >= 0L },
        )
    }

    fun currentRegionKey(installedRegionKeys: Set<String>): String? =
        currentRegion().regionKey?.takeIf { it in installedRegionKeys }

    /** Null means Explore follows the current region (or the first installed guide as fallback). */
    fun explicitBrowsedRegionKey(): String? = preferences.getString(KEY_BROWSED_REGION, null)

    fun browsedRegionKey(installedRegionKeys: List<String>): String? {
        if (installedRegionKeys.isEmpty()) return null
        return explicitBrowsedRegionKey()?.takeIf { it in installedRegionKeys }
            ?: currentRegionKey(installedRegionKeys.toSet())
            ?: installedRegionKeys.first()
    }

    fun selectBrowsedRegion(regionKey: String) {
        require(regionKey.isNotBlank()) { "A regional catalogue key is required." }
        preferences.edit().putString(KEY_BROWSED_REGION, regionKey).apply()
    }

    /** Starts Explore from the location-derived region without changing that region itself. */
    fun followCurrentRegion() {
        preferences.edit().remove(KEY_BROWSED_REGION).apply()
    }

    fun markCurrentAsLastKnown(): Boolean {
        val current = currentRegion()
        if (current.regionKey == null || current.source != CurrentRegionSource.CURRENT_FIX) return false
        preferences.edit()
            .putString(KEY_CURRENT_SOURCE, CurrentRegionSource.LAST_KNOWN_FIX.name)
            .apply()
        return true
    }

    /** Records only a unique, supported land-boundary match; every other result is unavailable. */
    fun recordLocation(
        latitude: Double,
        longitude: Double,
        locatedAtMs: Long,
        isCurrentFix: Boolean,
        installedRegionKeys: Set<String>,
    ): CurrentRegionContext {
        val assignment = RegionalBoundaryAssetLoader.load(appContext).assign(
            observationUuid = CURRENT_LOCATION_ID,
            latitude = latitude,
            longitude = longitude,
            obscured = false,
        )
        val regionKey = assignment.regionKey?.takeIf {
            assignment.earnsRegionalProgress && it in installedRegionKeys
        }
        val source = when {
            regionKey == null -> CurrentRegionSource.UNAVAILABLE
            isCurrentFix -> CurrentRegionSource.CURRENT_FIX
            else -> CurrentRegionSource.LAST_KNOWN_FIX
        }
        preferences.edit()
            .putString(KEY_CURRENT_SOURCE, source.name)
            .putLong(KEY_LOCATED_AT, locatedAtMs)
            .apply {
                if (regionKey == null) remove(KEY_CURRENT_REGION)
                else putString(KEY_CURRENT_REGION, regionKey)
            }
            .apply()
        return CurrentRegionContext(regionKey, source, locatedAtMs)
    }

    fun clear() = preferences.edit().clear().commit()

    companion object {
        private const val PREFERENCES = "region_context"
        private const val KEY_CURRENT_REGION = "current_region_key"
        private const val KEY_CURRENT_SOURCE = "current_region_source"
        private const val KEY_LOCATED_AT = "current_region_located_at_ms"
        private const val KEY_BROWSED_REGION = "browsed_region_key"
        private const val CURRENT_LOCATION_ID = "device-current-region"
    }
}
