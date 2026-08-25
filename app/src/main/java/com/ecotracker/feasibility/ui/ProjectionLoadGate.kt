package com.wildlife.feasibility.ui

/**
 * Monotonic ownership token for cancellable screen projections.
 *
 * SQLite/file work may finish after coroutine cancellation. A result may update screen state only
 * while its token is still current, preventing an older region/account projection from replacing
 * a newer request.
 */
internal class ProjectionLoadGate {
    private var latestToken = 0L

    fun next(): Long = ++latestToken

    fun isLatest(token: Long): Boolean = token == latestToken
}
