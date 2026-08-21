package com.wildlife.feasibility

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class BoundaryPoint(val longitude: Double, val latitude: Double)

data class RegionalBoundaryFeature(
    val regionKey: String?,
    val assignmentResult: String?,
    val polygons: List<List<List<BoundaryPoint>>>,
)

/** Local-only boundary assignment. It never infers a region from a nearest country or coast. */
class RegionalBoundaryAssigner(
    private val boundaryVersion: String,
    private val features: List<RegionalBoundaryFeature>,
) {
    fun assign(
        observationUuid: String,
        latitude: Double?,
        longitude: Double?,
        obscured: Boolean,
    ): ObservationRegion {
        if (obscured || latitude == null || longitude == null || !latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0
        ) {
            return observationUuid.uncertain()
        }
        val point = BoundaryPoint(longitude, latitude)
        val matches = features.filter { feature -> feature.contains(point) }
        if (matches.size != 1) {
            return if (matches.isEmpty()) {
                ObservationRegion(
                    observationUuid, RegionalAssignmentPolicy.MARINE_WORLDWIDE, boundaryVersion,
                    ObservationRegionAssignment.MARINE_WORLDWIDE,
                )
            } else {
                observationUuid.uncertain()
            }
        }
        val match = matches.single()
        return when {
            match.regionKey != null -> ObservationRegion(
                observationUuid, match.regionKey, boundaryVersion, ObservationRegionAssignment.LAND_POLYGON,
            )
            match.assignmentResult == "unsupported_for_regional_progression" -> ObservationRegion(
                observationUuid, null, boundaryVersion, ObservationRegionAssignment.UNSUPPORTED_LAND,
            )
            match.assignmentResult == RegionalAssignmentPolicy.MARINE_WORLDWIDE -> ObservationRegion(
                observationUuid, RegionalAssignmentPolicy.MARINE_WORLDWIDE, boundaryVersion,
                ObservationRegionAssignment.MARINE_WORLDWIDE,
            )
            else -> observationUuid.uncertain()
        }
    }

    private fun String.uncertain() = ObservationRegion(
        this, null, boundaryVersion, ObservationRegionAssignment.REGION_UNCERTAIN,
    )

    private fun RegionalBoundaryFeature.contains(point: BoundaryPoint): Boolean = polygons.any { polygon ->
        polygon.isNotEmpty() && pointInRing(point, polygon.first()) &&
            polygon.drop(1).none { hole -> pointInRing(point, hole) }
    }

    private fun pointInRing(point: BoundaryPoint, ring: List<BoundaryPoint>): Boolean {
        var inside = false
        ring.indices.forEach { index ->
            val a = ring[index]
            val b = ring[(index + 1) % ring.size]
            if ((a.latitude > point.latitude) != (b.latitude > point.latitude) &&
                point.longitude < (b.longitude - a.longitude) * (point.latitude - a.latitude) /
                (b.latitude - a.latitude) + a.longitude
            ) inside = !inside
        }
        return inside
    }
}

/** Reads the versioned boundary asset bundled with the application; no network or live place lookup. */
object RegionalBoundaryAssetLoader {
    const val BOUNDARY_VERSION = "regional-boundaries-v1"
    private const val ASSET = "boundaries/regional-boundaries-v1.geojson"

    fun load(context: Context): RegionalBoundaryAssigner {
        val root = context.assets.open(ASSET).bufferedReader().use { JSONObject(it.readText()) }
        val features = root.getJSONArray("features").toFeatureList()
        return RegionalBoundaryAssigner(BOUNDARY_VERSION, features)
    }

    private fun JSONArray.toFeatureList(): List<RegionalBoundaryFeature> = buildList {
        for (index in 0 until length()) {
            val feature = getJSONObject(index)
            val properties = feature.getJSONObject("properties")
            val coordinates = feature.getJSONObject("geometry").getJSONArray("coordinates")
            add(
                RegionalBoundaryFeature(
                    regionKey = properties.optString("region").takeIf(String::isNotBlank),
                    assignmentResult = properties.optString("assignment_result").takeIf(String::isNotBlank),
                    polygons = coordinates.toPolygons(),
                ),
            )
        }
    }

    private fun JSONArray.toPolygons(): List<List<List<BoundaryPoint>>> = buildList {
        for (polygonIndex in 0 until length()) {
            val polygon = getJSONArray(polygonIndex)
            add(buildList {
                for (ringIndex in 0 until polygon.length()) {
                    val ring = polygon.getJSONArray(ringIndex)
                    add(buildList {
                        for (pointIndex in 0 until ring.length()) {
                            val point = ring.getJSONArray(pointIndex)
                            add(BoundaryPoint(point.getDouble(0), point.getDouble(1)))
                        }
                    })
                }
            })
        }
    }
}
