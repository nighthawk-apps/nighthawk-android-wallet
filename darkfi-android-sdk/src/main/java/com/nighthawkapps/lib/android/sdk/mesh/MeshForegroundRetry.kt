package com.nighthawkapps.lib.android.sdk.mesh

/**
 * Bounded retries when Android rejects [android.app.Service.startForeground]
 * with [IllegalStateException] (including background-start restrictions).
 * A missing permission is not retried.
 */
internal object MeshForegroundRetry {
    const val MAX_ATTEMPTS: Int = 3
    private const val BASE_DELAY_MS: Long = 1_000L
    private const val MAX_DELAY_MS: Long = 5_000L

    fun shouldRetry(attemptsSoFar: Int): Boolean = attemptsSoFar < MAX_ATTEMPTS

    fun delayMs(attempt: Int): Long = (BASE_DELAY_MS * attempt.coerceAtLeast(1)).coerceAtMost(MAX_DELAY_MS)
}
