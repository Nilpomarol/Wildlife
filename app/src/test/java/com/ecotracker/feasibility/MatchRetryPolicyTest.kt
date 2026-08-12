package com.wildlife.feasibility

import org.junit.Assert.assertEquals
import org.junit.Test

class MatchRetryPolicyTest {
    @Test
    fun retriesBackOffAndCapAtTwoMinutes() {
        assertEquals(5_000L, MatchRetryPolicy.delayMs(0))
        assertEquals(15_000L, MatchRetryPolicy.delayMs(1))
        assertEquals(30_000L, MatchRetryPolicy.delayMs(2))
        assertEquals(60_000L, MatchRetryPolicy.delayMs(3))
        assertEquals(120_000L, MatchRetryPolicy.delayMs(4))
        assertEquals(120_000L, MatchRetryPolicy.delayMs(20))
    }
}
