package com.wildlife.feasibility

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Decides which public iNaturalist record a Wildlife capture is a photograph of.
 *
 * The matcher answers one question — *is this the same sighting?* — from the only two
 * pieces of evidence both sides hold: when it was recorded, and where. It never reads the
 * species, because the capture has no identification yet; that is the whole reason the
 * handoff exists.
 *
 * Three properties shape the design:
 *
 * **Evidence is scored, not gated.** Each axis yields a 0..1 agreement, combined as a
 * geometric mean so that strength on one axis cannot paper over failure on the other. A
 * record taken thirty seconds apart but forty kilometres away is not a three-quarters
 * match; it is barely a match at all.
 *
 * **Uncertainty is neutral, not favourable and not damning.** An obscured public
 * coordinate and a photograph carrying no coordinate at all are both *unmeasurable*, so
 * they score [NEUTRAL_LOCATION_SCORE] rather than a number computed from a coordinate that
 * does not mean what it appears to mean. The previous matcher ranked an unknown distance
 * as though it were ten kilometres, which is a measurement it never made.
 *
 * **Captures are resolved together.** [assign] scores every outstanding capture against
 * every available record in one pass, then hands out records best-first with each one
 * claimed at most once. Scoring a capture alone cannot see that its best candidate is
 * equally the best candidate for the capture below it — the single most likely way an
 * automatic link would be wrong, and the reason [MatchReason.COMPETING_CAPTURES] exists.
 */
object CandidateMatcher {
    /** Beyond a day apart, two records are not the same sighting whatever else agrees. */
    private const val MAX_TIME_DELTA_MS = 24L * 60L * 60L * 1_000L

    /** Full time credit: closer than this, the difference is clock drift between devices. */
    private const val EXACT_TIME_WINDOW_MS = 3L * 60L * 1_000L

    /** Past the window, agreement halves every interval — a slope rather than a cliff. */
    private const val TIME_HALF_LIFE_MS = 25L * 60L * 1_000L

    /**
     * An imported photograph with no EXIF timestamp is dated by its import, so its window
     * opens to the outing rather than the moment.
     */
    private const val UNRELIABLE_TIME_WINDOW_MS = 90L * 60L * 1_000L
    private const val UNRELIABLE_TIME_HALF_LIFE_MS = 5L * 60L * 60L * 1_000L

    /** Two measured coordinates further apart than this are not one sighting. */
    private const val MAX_DISTANCE_KM = 50.0

    /** Full location credit inside this radius, before either side's stated accuracy. */
    private const val EXACT_DISTANCE_KM = 0.15

    private const val DISTANCE_HALF_LIFE_KM = 1.2

    /** Applied when a coordinate exists but its owner never claimed it was accurate. */
    private const val UNRELIABLE_LOCATION_TOLERANCE_KM = 3.0

    /**
     * What an unmeasurable location is worth: neither evidence for nor against.
     *
     * Set so that perfect time agreement with no usable location lands at 0.71 — a strong
     * proposal to put in front of a person, and comfortably under [AUTOMATIC_SCORE].
     */
    private const val NEUTRAL_LOCATION_SCORE = 0.5

    /** A rival within this much of the leader makes the leader ambiguous. */
    private const val CLEAR_MARGIN = 0.15

    /** What confidence survives a dead heat: enough to propose, never enough to file. */
    private const val AMBIGUOUS_FLOOR = 0.6

    private const val AUTOMATIC_SCORE = 0.9
    private const val LIKELY_SCORE = 0.7

    /** A public record created before the handoff cannot be the upload of that handoff. */
    private const val CREATION_CLOCK_SKEW_MS = 5L * 60L * 1_000L

    /**
     * How many proposals one capture may offer. A day-wide window during a busy outing can
     * produce dozens of plausible records; past the fourth, the list stops being a decision
     * and becomes a search.
     */
    private const val MAX_PROPOSALS = 4

    /**
     * Matches every outstanding capture against every available record in one resolution.
     *
     * [linkedUuids] are records already claimed by a confirmed capture. They are removed
     * before scoring rather than filtered out afterwards, so a taken record cannot suppress
     * a live one by out-scoring it in the ambiguity comparison.
     */
    fun assign(
        markers: List<PendingMarker>,
        candidates: List<ObservationCandidate>,
        linkedUuids: Set<String> = emptySet(),
    ): MatchAssignment {
        val available = candidates.filterNot { it.uuid in linkedUuids }
        if (markers.isEmpty() || available.isEmpty()) {
            return MatchAssignment(emptyMap(), emptyList())
        }

        val scored = markers.flatMap { marker ->
            available.mapNotNull { candidate -> score(marker, candidate) }
        }
        if (scored.isEmpty()) return MatchAssignment(emptyMap(), emptyList())

        val byMarker = scored.groupBy { it.markerId }
        val byCandidate = scored.groupBy { it.candidate.uuid }

        val proposals = scored.map { pair ->
            // The nearest rival explanation, looking both ways: another record for this
            // capture, and another capture for this record.
            val rivalCandidate = byMarker.getValue(pair.markerId)
                .filter { it.candidate.uuid != pair.candidate.uuid }
                .maxOfOrNull { it.base }
            val rivalMarker = byCandidate.getValue(pair.candidate.uuid)
                .filter { it.markerId != pair.markerId }
                .maxOfOrNull { it.base }
            val candidateMargin = rivalCandidate?.let { pair.base - it }
            val markerMargin = rivalMarker?.let { pair.base - it }
            val margin = listOfNotNull(candidateMargin, markerMargin).minOrNull()

            val reasons = pair.reasons.toMutableList()
            if (candidateMargin != null && candidateMargin < CLEAR_MARGIN) {
                reasons += MatchReason.COMPETING_CANDIDATES
            }
            if (markerMargin != null && markerMargin < CLEAR_MARGIN) {
                reasons += MatchReason.COMPETING_CAPTURES
            }

            val confidence = (pair.base * ambiguityFactor(margin)).coerceIn(0.0, 1.0)
            MatchProposal(
                markerId = pair.markerId,
                candidate = pair.candidate,
                confidence = confidence,
                band = band(confidence, pair, margin),
                timeDeltaMinutes = pair.deltaMs / 60_000L,
                distanceKm = pair.distanceKm,
                reasons = reasons,
            )
        }

        // Hand out records best-first. A capture that has taken one is done, and a record
        // that was taken is gone — which is what makes this an assignment rather than a
        // set of independent per-capture opinions.
        val automatic = mutableListOf<MatchProposal>()
        val claimedMarkers = mutableSetOf<String>()
        val claimedCandidates = mutableSetOf<String>()
        proposals.asSequence()
            .filter { it.band == MatchBand.AUTOMATIC }
            .sortedByDescending(MatchProposal::confidence)
            .forEach { proposal ->
                if (proposal.markerId in claimedMarkers) return@forEach
                if (proposal.candidate.uuid in claimedCandidates) return@forEach
                claimedMarkers += proposal.markerId
                claimedCandidates += proposal.candidate.uuid
                automatic += proposal
            }

        val remaining = proposals
            .filterNot { it.markerId in claimedMarkers || it.candidate.uuid in claimedCandidates }
            .groupBy(MatchProposal::markerId)
            .mapValues { (_, forMarker) ->
                forMarker.sortedByDescending(MatchProposal::confidence).take(MAX_PROPOSALS)
            }

        return MatchAssignment(proposals = remaining, automatic = automatic)
    }

    /**
     * The single-capture view, for callers counting outstanding work rather than resolving
     * it. It cannot report [MatchReason.COMPETING_CAPTURES] — with one capture in hand
     * there is no other capture to compete — so a pairing may be graded more generously
     * here than the same pairing inside a full [assign].
     */
    fun proposals(
        marker: PendingMarker,
        candidates: List<ObservationCandidate>,
        linkedUuids: Set<String> = emptySet(),
    ): List<MatchProposal> {
        val assignment = assign(listOf(marker), candidates, linkedUuids)
        return assignment.automatic.filter { it.markerId == marker.id } +
            assignment.proposals[marker.id].orEmpty()
    }

    private fun score(marker: PendingMarker, candidate: ObservationCandidate): ScoredPair? {
        // Already answered for this capture. Re-proposing it would undo the answer.
        if (candidate.uuid in marker.rejectedObservationUuids) return null
        if (
            marker.sharedAtMs != null && candidate.createdAtMs != null &&
            candidate.createdAtMs < marker.sharedAtMs - CREATION_CLOCK_SKEW_MS
        ) {
            return null
        }
        val deltaMs = abs(candidate.observedAtMs - marker.capturedAtMs)
        if (deltaMs > MAX_TIME_DELTA_MS) return null

        val distanceKm = distanceKm(
            marker.latitude, marker.longitude, candidate.latitude, candidate.longitude,
        )
        // Only a measured separation can rule a record out. An obscured coordinate is
        // deliberately displaced by iNaturalist, so its distance is not a measurement.
        if (!candidate.obscured && distanceKm != null && distanceKm > MAX_DISTANCE_KM) return null

        val reasons = mutableListOf<MatchReason>()
        val timeScore = timeScore(deltaMs, marker.capturedAtReliable, reasons)
        val locationScore = locationScore(marker, candidate, distanceKm, reasons)

        return ScoredPair(
            markerId = marker.id,
            candidate = candidate,
            base = sqrt(timeScore * locationScore),
            deltaMs = deltaMs,
            distanceKm = distanceKm,
            locationMeasured = !candidate.obscured && distanceKm != null,
            reliableMarker = marker.capturedAtReliable && marker.locationReliable,
            reasons = reasons,
        )
    }

    private fun timeScore(
        deltaMs: Long,
        reliable: Boolean,
        reasons: MutableList<MatchReason>,
    ): Double {
        if (!reliable) reasons += MatchReason.CAPTURE_TIME_UNRELIABLE
        val window = if (reliable) EXACT_TIME_WINDOW_MS else UNRELIABLE_TIME_WINDOW_MS
        val halfLife = if (reliable) TIME_HALF_LIFE_MS else UNRELIABLE_TIME_HALF_LIFE_MS
        val excess = max(0L, deltaMs - window)
        reasons += if (excess == 0L) MatchReason.TIME_ALIGNED else MatchReason.TIME_APPROXIMATE
        return decay(excess.toDouble(), halfLife.toDouble())
    }

    private fun locationScore(
        marker: PendingMarker,
        candidate: ObservationCandidate,
        distanceKm: Double?,
        reasons: MutableList<MatchReason>,
    ): Double {
        if (candidate.obscured) {
            reasons += MatchReason.LOCATION_OBSCURED
            return NEUTRAL_LOCATION_SCORE
        }
        if (distanceKm == null) {
            reasons += MatchReason.LOCATION_UNKNOWN
            return NEUTRAL_LOCATION_SCORE
        }
        val tolerance = EXACT_DISTANCE_KM +
            (candidate.positionalAccuracyM ?: 0) / 1_000.0 +
            if (marker.locationReliable) 0.0 else UNRELIABLE_LOCATION_TOLERANCE_KM
        val excess = max(0.0, distanceKm - tolerance)
        reasons += if (excess == 0.0) {
            MatchReason.LOCATION_ALIGNED
        } else {
            MatchReason.LOCATION_APPROXIMATE
        }
        return decay(excess, DISTANCE_HALF_LIFE_KM)
    }

    /** Halves every [halfLife] of [excess]: 1.0 at no excess, asymptotic to zero. */
    private fun decay(excess: Double, halfLife: Double): Double = 0.5.pow(excess / halfLife)

    /**
     * How much a near rival costs the leader. A dead heat keeps [AMBIGUOUS_FLOOR] of the
     * score — still worth proposing, never enough to file — rising to full value once the
     * leader is [CLEAR_MARGIN] ahead.
     */
    private fun ambiguityFactor(margin: Double?): Double {
        if (margin == null) return 1.0
        val closeness = (margin / CLEAR_MARGIN).coerceIn(0.0, 1.0)
        return AMBIGUOUS_FLOOR + (1.0 - AMBIGUOUS_FLOOR) * closeness
    }

    /**
     * Filing without asking demands more than a high number. The capture must vouch for
     * its own time and place, the public coordinate must be real rather than obscured, and
     * nothing else may explain the pairing nearly as well.
     */
    private fun band(confidence: Double, pair: ScoredPair, margin: Double?): MatchBand = when {
        confidence >= AUTOMATIC_SCORE &&
            pair.locationMeasured &&
            pair.reliableMarker &&
            (margin == null || margin >= CLEAR_MARGIN) -> MatchBand.AUTOMATIC
        confidence >= LIKELY_SCORE -> MatchBand.LIKELY
        else -> MatchBand.POSSIBLE
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
        return earthRadiusKm * 2 * atan2(sqrt(a), sqrt(max(0.0, 1 - a)))
    }

    /** One capture-and-record pairing, before rival pairings are taken into account. */
    private data class ScoredPair(
        val markerId: String,
        val candidate: ObservationCandidate,
        val base: Double,
        val deltaMs: Long,
        val distanceKm: Double?,
        val locationMeasured: Boolean,
        val reliableMarker: Boolean,
        val reasons: List<MatchReason>,
    )
}
