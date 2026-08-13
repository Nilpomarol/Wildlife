package com.wildlife.feasibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDataInventoryTest {
    @Test
    fun `test report contains operational counts without identity or location fields`() {
        val report = LocalDataInventory(
            accountLinked = true,
            cachedObservations = 17,
            hiddenMapObservations = 2,
            catalogueSpecies = 580,
            pendingHandoffs = 1,
            privateCaptureFiles = 3,
            privateCaptureBytes = 2_048,
        ).privacySafeTestReport(observationSyncErrorPresent = true)

        assertTrue(report.contains("Cached observations: 17"))
        assertTrue(report.contains("Map-hidden observations: 2"))
        assertTrue(report.contains("Observation sync error present: yes"))
        assertFalse(report.contains("username", ignoreCase = true))
        assertFalse(report.contains("user id", ignoreCase = true))
        assertFalse(report.contains("latitude", ignoreCase = true))
        assertFalse(report.contains("longitude", ignoreCase = true))
        assertFalse(report.contains("file path", ignoreCase = true))
    }

    @Test
    fun `stored byte counts use compact readable units`() {
        assertTrue(formatBytes(512) == "512 B")
        assertTrue(formatBytes(2_048) == "2 KiB")
        assertTrue(formatBytes(3L * 1_024 * 1_024) == "3 MiB")
    }
}
