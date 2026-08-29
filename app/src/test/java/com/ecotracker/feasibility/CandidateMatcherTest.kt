package com.wildlife.feasibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateMatcherTest {
    private val marker = PendingMarker(
        id = "marker",
        imageUri = "content://photo",
        source = "camera",
        capturedAtMs = 1_000_000L,
        latitude = 41.3874,
        longitude = 2.1686,
    )

    // ------------------------------------------------------------------ single capture

    @Test
    fun uniqueCloseCandidateFilesAutomatically() {
        val proposals = CandidateMatcher.proposals(
            marker,
            listOf(candidate(time = marker.capturedAtMs + 60_000L)),
        )

        assertEquals(1, proposals.size)
        assertEquals(MatchBand.AUTOMATIC, proposals.single().band)
        assertTrue(proposals.single().confidence > 0.9)
    }

    @Test
    fun automaticMatchIsReportedAsAutomatic() {
        val assignment = CandidateMatcher.assign(
            listOf(marker),
            listOf(candidate(time = marker.capturedAtMs + 60_000L)),
        )

        assertEquals(1, assignment.automatic.size)
        assertEquals("observation", assignment.automatic.single().candidate.uuid)
        // A record that was filed is no longer offered as something to choose.
        assertTrue(assignment.proposals[marker.id].isNullOrEmpty())
    }

    @Test
    fun distantInTimeCandidateStaysAProposal() {
        val proposals = CandidateMatcher.proposals(
            marker,
            listOf(candidate(time = marker.capturedAtMs + 6 * 60 * 60_000L)),
        )

        assertEquals(1, proposals.size)
        assertTrue(proposals.single().band != MatchBand.AUTOMATIC)
    }

    @Test
    fun agreementOnOneAxisDoesNotCarryFailureOnTheOther() {
        // Half a minute apart, but twelve kilometres away. The geometric mean is what stops
        // near-perfect time from dragging an implausible distance over the automatic bar.
        val proposals = CandidateMatcher.proposals(
            marker,
            listOf(candidate(time = marker.capturedAtMs + 30_000L, latitude = 41.495)),
        )

        assertEquals(1, proposals.size)
        assertTrue(proposals.single().confidence < 0.4)
        assertEquals(MatchBand.POSSIBLE, proposals.single().band)
    }

    // ------------------------------------------------------------------ ambiguity

    @Test
    fun twoEquallyGoodCandidatesBothNeedConfirmation() {
        val proposals = CandidateMatcher.proposals(
            marker,
            listOf(
                candidate(uuid = "one", time = marker.capturedAtMs + 60_000L),
                candidate(uuid = "two", time = marker.capturedAtMs + 90_000L),
            ),
        )

        assertEquals(2, proposals.size)
        assertTrue(proposals.none { it.band == MatchBand.AUTOMATIC })
        assertTrue(proposals.all { MatchReason.COMPETING_CANDIDATES in it.reasons })
    }

    @Test
    fun twoCapturesCompetingForOneRecordAreNotBothFiled() {
        val second = marker.copy(id = "second", capturedAtMs = marker.capturedAtMs + 120_000L)

        val assignment = CandidateMatcher.assign(
            listOf(marker, second),
            listOf(candidate(time = marker.capturedAtMs + 60_000L)),
        )

        assertTrue(assignment.automatic.isEmpty())
        val all = assignment.all()
        assertTrue(all.getValue(marker.id).all { MatchReason.COMPETING_CAPTURES in it.reasons })
        assertTrue(all.getValue("second").all { MatchReason.COMPETING_CAPTURES in it.reasons })
    }

    @Test
    fun oneRecordIsNeverFiledToTwoCaptures() {
        // Both captures sit on the same record, but one is clearly closer to it. Only the
        // better pairing may be filed, and the record must not appear a second time.
        val distant = marker.copy(
            id = "distant",
            capturedAtMs = marker.capturedAtMs + 4 * 60 * 60_000L,
        )

        val assignment = CandidateMatcher.assign(
            listOf(marker, distant),
            listOf(candidate(time = marker.capturedAtMs + 60_000L)),
        )

        assertEquals(1, assignment.automatic.size)
        assertEquals(marker.id, assignment.automatic.single().markerId)
        assertNull(assignment.proposals["distant"])
    }

    @Test
    fun twoCapturesAndTwoRecordsResolveToTheirOwnMatches() {
        val later = marker.copy(id = "later", capturedAtMs = marker.capturedAtMs + 3_600_000L)
        val nearMarker = candidate(uuid = "near", time = marker.capturedAtMs + 60_000L)
        val nearLater = candidate(uuid = "far", time = later.capturedAtMs + 60_000L)

        val assignment = CandidateMatcher.assign(listOf(marker, later), listOf(nearMarker, nearLater))

        assertEquals(2, assignment.automatic.size)
        assertEquals(
            mapOf(marker.id to "near", "later" to "far"),
            assignment.automatic.associate { it.markerId to it.candidate.uuid },
        )
    }

    @Test
    fun aClearlyBetterCandidateStillFilesAutomatically() {
        val proposals = CandidateMatcher.proposals(
            marker,
            listOf(
                candidate(uuid = "near", time = marker.capturedAtMs + 60_000L),
                candidate(uuid = "hoursLater", time = marker.capturedAtMs + 8 * 60 * 60_000L),
            ),
        )

        assertEquals(MatchBand.AUTOMATIC, proposals.first().band)
        assertEquals("near", proposals.first().candidate.uuid)
    }

    // ------------------------------------------------------------------ uncertainty

    @Test
    fun obscuredCandidateIsNeverFiledAutomatically() {
        // Perfect time agreement, which on an unobscured record would file itself. The
        // neutral location score caps it at a proposal instead.
        val proposals = CandidateMatcher.proposals(
            marker,
            listOf(
                candidate(
                    time = marker.capturedAtMs + 60_000L,
                    obscured = true,
                    latitude = 41.45,
                    longitude = 2.25,
                ),
            ),
        )

        assertEquals(MatchBand.LIKELY, proposals.single().band)
        assertTrue(MatchReason.LOCATION_OBSCURED in proposals.single().reasons)
    }

    @Test
    fun obscuredCandidateIsNotJudgedOnItsRandomisedDistance() {
        // The displaced coordinate lands 25km away. That is not a measurement, so it must
        // not cost the candidate anything relative to an obscured record next door.
        val nearby = CandidateMatcher.proposals(
            marker,
            listOf(candidate(obscured = true, latitude = 41.3875, longitude = 2.1687)),
        ).single()
        val displaced = CandidateMatcher.proposals(
            marker,
            listOf(candidate(obscured = true, latitude = 41.61, longitude = 2.1687)),
        ).single()

        assertEquals(nearby.confidence, displaced.confidence, 1e-9)
    }

    @Test
    fun candidateWithoutCoordinatesRestsOnTimeAlone() {
        val proposals = CandidateMatcher.proposals(
            marker,
            listOf(candidate(latitude = null, longitude = null)),
        )

        assertEquals(1, proposals.size)
        assertNull(proposals.single().distanceKm)
        assertTrue(MatchReason.LOCATION_UNKNOWN in proposals.single().reasons)
        assertTrue(proposals.single().band != MatchBand.AUTOMATIC)
    }

    @Test
    fun statedAccuracyWidensWhatCountsAsTheSamePlace() {
        // 900m apart. Against a record iNaturalist accurate to 1.5km that is the same
        // place; against a record claiming no accuracy at all it is a separation.
        val distant = candidate(latitude = 41.3955)
        val precise = CandidateMatcher.proposals(marker, listOf(distant)).single()
        val coarse = CandidateMatcher.proposals(
            marker,
            listOf(distant.copy(positionalAccuracyM = 1_500)),
        ).single()

        assertTrue(coarse.confidence > precise.confidence)
        assertTrue(MatchReason.LOCATION_ALIGNED in coarse.reasons)
        assertTrue(MatchReason.LOCATION_APPROXIMATE in precise.reasons)
    }

    @Test
    fun importedPhotoWithNoExifTimeIsJudgedOnAWiderWindow() {
        val imported = marker.copy(capturedAtReliable = false)
        val hourApart = candidate(time = marker.capturedAtMs + 60 * 60_000L)

        val importedMatch = CandidateMatcher.proposals(imported, listOf(hourApart)).single()
        val exactMatch = CandidateMatcher.proposals(marker, listOf(hourApart)).single()

        assertTrue(importedMatch.confidence > exactMatch.confidence)
        assertTrue(MatchReason.CAPTURE_TIME_UNRELIABLE in importedMatch.reasons)
        // Widened, but never trusted enough to file without asking.
        assertTrue(importedMatch.band != MatchBand.AUTOMATIC)
    }

    @Test
    fun captureWithoutAReliableFixIsNeverFiledAutomatically() {
        val drifting = marker.copy(locationReliable = false)

        val proposals = CandidateMatcher.proposals(
            drifting,
            listOf(candidate(time = marker.capturedAtMs + 60_000L)),
        )

        assertFalse(proposals.single().band == MatchBand.AUTOMATIC)
    }

    // ------------------------------------------------------------------ hard limits

    @Test
    fun distantNonObscuredCandidateIsRejected() {
        val proposals = CandidateMatcher.proposals(
            marker,
            listOf(candidate(latitude = 42.0, longitude = 3.0)),
        )

        assertTrue(proposals.isEmpty())
    }

    @Test
    fun candidateAfterOneDayIsRejected() {
        val proposals = CandidateMatcher.proposals(
            marker,
            listOf(candidate(time = marker.capturedAtMs + 25 * 60 * 60_000L)),
        )

        assertTrue(proposals.isEmpty())
    }

    @Test
    fun observationCreatedBeforeHandoffIsRejected() {
        val sharedMarker = marker.copy(sharedAtMs = 2_000_000L)
        val proposals = CandidateMatcher.proposals(
            sharedMarker,
            listOf(candidate(createdAt = 1_000_000L)),
        )

        assertTrue(proposals.isEmpty())
    }

    @Test
    fun observationCreatedAfterHandoffCanMatchOldPhoto() {
        val sharedMarker = marker.copy(sharedAtMs = 10_000_000L)
        val proposals = CandidateMatcher.proposals(
            sharedMarker,
            listOf(candidate(createdAt = 10_100_000L)),
        )

        assertEquals(1, proposals.size)
    }

    @Test
    fun alreadyLinkedRecordsAreNotOfferedAgain() {
        val proposals = CandidateMatcher.proposals(
            marker,
            listOf(candidate(time = marker.capturedAtMs + 60_000L)),
            linkedUuids = setOf("observation"),
        )

        assertTrue(proposals.isEmpty())
    }

    @Test
    fun aRejectedRecordIsNeverProposedForThatCaptureAgain() {
        // The pairing that would otherwise file itself, after the user said it was not theirs.
        val answered = marker.copy(rejectedObservationUuids = setOf("observation"))

        val proposals = CandidateMatcher.proposals(
            answered,
            listOf(candidate(time = marker.capturedAtMs + 60_000L)),
        )

        assertTrue(proposals.isEmpty())
    }

    @Test
    fun aRejectionBindsToOneCaptureRatherThanTheRecord() {
        // The same record may still be the right answer for a different photograph.
        val answered = marker.copy(rejectedObservationUuids = setOf("observation"))
        val other = marker.copy(id = "other")

        val assignment = CandidateMatcher.assign(
            listOf(answered, other),
            listOf(candidate(time = marker.capturedAtMs + 60_000L)),
        )

        assertEquals(1, assignment.automatic.size)
        assertEquals("other", assignment.automatic.single().markerId)
    }

    @Test
    fun proposalsPerCaptureAreCapped() {
        val crowd = (1..12).map { index ->
            candidate(uuid = "candidate-$index", time = marker.capturedAtMs + index * 60_000L)
        }

        val proposals = CandidateMatcher.proposals(marker, crowd)

        assertEquals(4, proposals.size)
        // Still ordered by how well each explains the capture.
        assertEquals(
            proposals.map(MatchProposal::confidence).sortedDescending(),
            proposals.map(MatchProposal::confidence),
        )
    }

    // ------------------------------------------------------------------ carried identity

    @Test
    fun proposalCarriesTheRecordIdentityTheScreenNeeds() {
        val proposals = CandidateMatcher.proposals(
            marker,
            listOf(
                candidate(time = marker.capturedAtMs + 60_000L).copy(
                    label = "Common kingfisher",
                    photoUrl = "https://example.invalid/photo.jpg",
                    taxonId = 4_930L,
                ),
            ),
        )

        val proposed = proposals.single().candidate
        assertEquals("Common kingfisher", proposed.label)
        assertEquals("https://example.invalid/photo.jpg", proposed.photoUrl)
        assertEquals(4_930L, proposed.taxonId)
        assertNotNull(proposals.single().distanceKm)
    }

    private fun candidate(
        uuid: String = "observation",
        time: Long = marker.capturedAtMs + 5 * 60_000L,
        latitude: Double? = 41.388,
        longitude: Double? = 2.169,
        obscured: Boolean = false,
        createdAt: Long? = null,
    ) = ObservationCandidate(uuid, time, latitude, longitude, obscured, createdAt)
}
