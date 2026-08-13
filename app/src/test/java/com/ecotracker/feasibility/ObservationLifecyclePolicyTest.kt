package com.wildlife.feasibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationLifecyclePolicyTest {
    @Test
    fun missingAndOldSyncsAreStale() {
        val now = 200_000_000L

        assertTrue(ObservationLifecyclePolicy.isStale(null, now))
        assertTrue(
            ObservationLifecyclePolicy.isStale(
                now - ObservationLifecyclePolicy.STALE_AFTER_MS - 1,
                now,
            ),
        )
        assertFalse(ObservationLifecyclePolicy.isStale(now - 60_000, now))
    }

    @Test
    fun pendingGroupsAreCountedOnceAndBecomeReadyWhenCandidatesExist() {
        val markers = listOf(
            marker("photo-one", handoffId = "handoff"),
            marker("photo-two", handoffId = "handoff"),
        )
        val candidates = listOf(
            ObservationCandidate(
                uuid = "candidate",
                observedAtMs = 1_000,
                latitude = 41.0,
                longitude = 2.0,
                obscured = false,
                createdAtMs = 2_000,
            ),
        )

        val status = ObservationLifecyclePolicy.pendingStatus(markers, candidates)

        assertEquals(1, status.awaitingPublicRecord)
        assertEquals(1, status.readyToReview)
    }

    @Test
    fun observationAlreadyMatchedElsewhereIsNotOfferedAgain() {
        val pending = marker("pending", handoffId = "pending-handoff")
        val confirmed = marker("confirmed", handoffId = "confirmed-handoff").copy(
            state = MarkerState.CONFIRMED,
            matchedObservationUuid = "candidate",
        )
        val candidate = ObservationCandidate(
            uuid = "candidate",
            observedAtMs = 1_000,
            latitude = 41.0,
            longitude = 2.0,
            obscured = false,
            createdAtMs = 2_000,
        )

        val status = ObservationLifecyclePolicy.pendingStatus(
            listOf(pending, confirmed),
            listOf(candidate),
        )

        assertEquals(1, status.awaitingPublicRecord)
        assertEquals(0, status.readyToReview)
    }

    @Test
    fun qualityTransitionRequiresAStoredDifferentPreviousState() {
        val observation = syncedObservation(qualityGrade = "research")

        assertEquals(
            null,
            ObservationLifecyclePolicy.qualityTransition(null, observation, 2_000),
        )
        assertEquals(
            null,
            ObservationLifecyclePolicy.qualityTransition("research", observation, 2_000),
        )
        val transition = ObservationLifecyclePolicy.qualityTransition(
            "needs_id",
            observation,
            2_000,
        )
        assertEquals("needs_id", transition?.fromQualityGrade)
        assertEquals("research", transition?.toQualityGrade)
        assertEquals("European robin", transition?.label)
    }

    private fun marker(id: String, handoffId: String) = PendingMarker(
        id = id,
        imageUri = "content://$id",
        source = "camera",
        capturedAtMs = 1_000,
        latitude = 41.0,
        longitude = 2.0,
        state = MarkerState.PENDING,
        sharedAtMs = 1_500,
        handoffId = handoffId,
    )

    private fun syncedObservation(qualityGrade: String) = SyncedObservation(
        id = 1,
        uuid = "observation",
        taxonId = 42,
        taxonRank = "species",
        collectionTaxonId = 42,
        collectionTaxonRank = "species",
        label = "European robin",
        observedAtMs = 1_000,
        latitude = 41.0,
        longitude = 2.0,
        obscured = false,
        createdAtMs = 1_500,
        qualityGrade = qualityGrade,
        photoUrl = null,
        confirmed = true,
    )
}
