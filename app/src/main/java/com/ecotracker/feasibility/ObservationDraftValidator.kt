package com.wildlife.feasibility

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object ObservationDraftValidator {
    private const val MAX_PHOTO_TIME_SPAN_MS = 30L * 60L * 1_000L
    private const val MAX_PHOTO_DISTANCE_KM = 2.0

    fun problem(markers: List<PendingMarker>): String? {
        if (markers.isEmpty()) return "Select at least one photo."
        if (markers.any { !it.capturedAtReliable }) {
            return "Confirm the original date and time for every selected photo first."
        }
        val earliest = markers.minOf(PendingMarker::capturedAtMs)
        val latest = markers.maxOf(PendingMarker::capturedAtMs)
        if (latest - earliest > MAX_PHOTO_TIME_SPAN_MS) {
            return "Selected photos are over 30 minutes apart. Create separate observations."
        }
        val located = markers.filter { it.locationReliable && it.latitude != null && it.longitude != null }
        for (firstIndex in located.indices) {
            for (secondIndex in firstIndex + 1 until located.size) {
                if (distanceKm(located[firstIndex], located[secondIndex]) > MAX_PHOTO_DISTANCE_KM) {
                    return "Selected photos are over 2 km apart. Create separate observations."
                }
            }
        }
        return null
    }

    private fun distanceKm(first: PendingMarker, second: PendingMarker): Double {
        val lat1 = requireNotNull(first.latitude)
        val lon1 = requireNotNull(first.longitude)
        val lat2 = requireNotNull(second.latitude)
        val lon2 = requireNotNull(second.longitude)
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return 6_371.0 * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
