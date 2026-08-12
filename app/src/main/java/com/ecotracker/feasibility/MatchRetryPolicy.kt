package com.wildlife.feasibility

object MatchRetryPolicy {
    private val delaysMs = longArrayOf(5_000L, 15_000L, 30_000L, 60_000L, 120_000L)

    fun delayMs(attempt: Int): Long = delaysMs[attempt.coerceIn(0, delaysMs.lastIndex)]
}
