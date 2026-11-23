package com.nighthawkapps.lib.android.sdk.mesh

/**
 * Bounded retries when Android rejects [android.app.Service.startForeground]
 * with [IllegalStateException] (including background-start restrictions).
 * A missing permission is not retried.
 */
internal object MeshForegroundRetry {
    const val MAX_ATTEMPTS: Int = 3

    fun shouldRetry(attemptsSoFar: Int): Boolean = attemptsSoFar < MAX_ATTEMPTS

    fun delayMs(attempt: Int): Long = (1_000L * attempt.coerceAtLeast(1)).coerceAtMost(5_000L)
}
