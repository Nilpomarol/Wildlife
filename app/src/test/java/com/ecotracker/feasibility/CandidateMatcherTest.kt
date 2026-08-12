package com.wildlife.feasibility

import org.junit.Assert.assertEquals
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

    @Test
    fun uniqueCloseCandidateIsHighConfidence() {
        val candidate = candidate(time = marker.capturedAtMs + 5 * 60_000L)

        val proposals = CandidateMatcher.proposals(marker, listOf(candidate))

        assertEquals(1, proposals.size)
        assertEquals(MatchConfidence.HIGH, proposals.single().confidence)
    }

    @Test
    fun multipleCloseCandidatesRequireConfirmation() {
        val proposals = CandidateMatcher.proposals(
            marker,
            listOf(
                candidate(uuid = "one", time = marker.capturedAtMs + 5 * 60_000L),
                candidate(uuid = "two", time = marker.capturedAtMs + 6 * 60_000L),
            ),
        )

        assertEquals(2, proposals.size)
        assertTrue(proposals.all { it.confidence == MatchConfidence.NEEDS_CONFIRMATION })
    }

    @Test
    fun obscuredCandidateRequiresConfirmation() {
        val proposals = CandidateMatcher.proposals(
            marker,
            listOf(candidate(obscured = true, latitude = 41.45, longitude = 2.25)),
        )

        assertEquals(MatchConfidence.NEEDS_CONFIRMATION, proposals.single().confidence)
    }

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

    private fun candidate(
        uuid: String = "observation",
        time: Long = marker.capturedAtMs + 5 * 60_000L,
        latitude: Double? = 41.388,
        longitude: Double? = 2.169,
        obscured: Boolean = false,
        createdAt: Long? = null,
    ) = ObservationCandidate(uuid, time, latitude, longitude, obscured, createdAt)
}
