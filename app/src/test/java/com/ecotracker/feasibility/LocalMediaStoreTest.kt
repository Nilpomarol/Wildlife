package com.wildlife.feasibility

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalMediaStoreTest {
    @Test
    fun `download respects both per-file and total storage limits`() {
        assertEquals(12L, permittedDownloadBytes(20L, 12L, 100L))
        assertEquals(5L, permittedDownloadBytes(95L, 12L, 100L))
        assertEquals(0L, permittedDownloadBytes(100L, 12L, 100L))
    }

    @Test
    fun `only non-empty files within their asset limit are reusable`() {
        assertEquals(false, validStoredAsset(0L, 12L))
        assertEquals(true, validStoredAsset(12L, 12L))
        assertEquals(false, validStoredAsset(13L, 12L))
    }
}
