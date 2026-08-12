package com.wildlife.feasibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalObservationRecordsTest {
    @Test
    fun deletingOneObservationRemovesEveryPhotoInItsGroupOnly() {
        val grouped = marker("one").copy(handoffId = "observation")
        val groupedSecond = marker("two").copy(handoffId = "observation")
        val separate = marker("three")

        val remaining = LocalObservationRecords.removeGroup(
            listOf(grouped, groupedSecond, separate),
            "observation",
        )

        assertEquals(1, remaining.size)
        assertTrue(remaining.single().id == "three")
    }

    private fun marker(id: String) = PendingMarker(
        id = id,
        imageUri = "content://$id",
        source = "test",
        capturedAtMs = 1_000L,
        latitude = null,
        longitude = null,
    )
}
