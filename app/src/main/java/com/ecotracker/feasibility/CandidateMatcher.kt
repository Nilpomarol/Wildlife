package com.wildlife.feasibility

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object CandidateMatcher {
    private const val MAX_TIME_DELTA_MS = 24L * 60L * 60L * 1_000L
    private const val HIGH_TIME_DELTA_MS = 20L * 60L * 1_000L
    private const val HIGH_DISTANCE_KM = 2.0
    private const val CONFIRM_DISTANCE_KM = 50.0
    private const val CREATION_CLOCK_SKEW_MS = 5L * 60L * 1_000L

    fun proposals(marker: PendingMarker, candidates: List<ObservationCandidate>): List<MatchProposal> {
        val plausible = candidates.mapNotNull { candidate ->
            if (
                marker.sharedAtMs != null && candidate.createdAtMs != null &&
                candidate.createdAtMs < marker.sharedAtMs - CREATION_CLOCK_SKEW_MS
            ) return@mapNotNull null
            val delta = abs(candidate.observedAtMs - marker.capturedAtMs)
            if (delta > MAX_TIME_DELTA_MS) return@mapNotNull null

            val distance = distanceKm(
                marker.latitude,
                marker.longitude,
                candidate.latitude,
                candidate.longitude,
            )
            if (distance != null && distance > CONFIRM_DISTANCE_KM && !candidate.obscured) {
                return@mapNotNull null
            }

            RankedCandidate(candidate, delta, distance)
        }.sortedBy { ranked ->
            ranked.deltaMs / 60_000.0 + (ranked.distanceKm ?: 10.0) * 5.0
        }

        return plausible.map { ranked ->
            val isUnique = plausible.size == 1
            val highConfidence = isUnique &&
                !ranked.candidate.obscured &&
                ranked.deltaMs <= HIGH_TIME_DELTA_MS &&
                ranked.distanceKm != null &&
                ranked.distanceKm <= HIGH_DISTANCE_KM

            MatchProposal(
                markerId = marker.id,
                candidate = ranked.candidate,
                confidence = if (highConfidence) {
                    MatchConfidence.HIGH
                } else {
                    MatchConfidence.NEEDS_CONFIRMATION
                },
                timeDeltaMinutes = ranked.deltaMs / 60_000L,
                distanceKm = ranked.distanceKm,
            )
        }
    }

    private fun distanceKm(
        lat1: Double?,
        lon1: Double?,
        lat2: Double?,
        lon2: Double?,
    ): Double? {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) return null
        val earthRadiusKm = 6_371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return earthRadiusKm * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private data class RankedCandidate(
        val candidate: ObservationCandidate,
        val deltaMs: Long,
        val distanceKm: Double?,
    )
}
