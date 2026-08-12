package com.wildlife.feasibility

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationDraftValidatorTest {
    @Test
    fun acceptsMultiplePhotosFromOneSighting() {
        assertNull(
            ObservationDraftValidator.problem(
                listOf(marker("one", 1_000_000L, 41.3874, 2.1686), marker("two", 1_300_000L, 41.388, 2.169)),
            ),
        )
    }

    @Test
    fun rejectsPhotosFarApartInTime() {
        val problem = ObservationDraftValidator.problem(
            listOf(marker("one", 1_000_000L), marker("two", 3_000_001L)),
        )
        assertTrue(problem.orEmpty().contains("30 minutes"))
    }

    @Test
    fun rejectsPhotosFarApartInSpace() {
        val problem = ObservationDraftValidator.problem(
            listOf(marker("one", 1_000_000L, 41.3874, 2.1686), marker("two", 1_100_000L, 42.0, 3.0)),
        )
        assertTrue(problem.orEmpty().contains("2 km"))
    }

    @Test
    fun rejectsUnconfirmedOriginalTime() {
        val problem = ObservationDraftValidator.problem(
            listOf(marker("one", 1_000_000L).copy(capturedAtReliable = false)),
        )
        assertTrue(problem.orEmpty().contains("date and time"))
    }

    private fun marker(
        id: String,
        time: Long,
        latitude: Double? = null,
        longitude: Double? = null,
    ) = PendingMarker(
        id = id,
        imageUri = "content://$id",
        source = "test",
        capturedAtMs = time,
        latitude = latitude,
        longitude = longitude,
        locationReliable = latitude != null && longitude != null,
    )
}
