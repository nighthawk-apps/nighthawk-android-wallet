package com.nighthawkapps.lib.android.ui.splash

import kotlin.math.exp

/**
 * Splash progress for the Nym mixnet bootstrap.
 *
 * The native client reports no intermediate steps, so the bar is an estimate from elapsed time:
 * it eases toward [MAX_ESTIMATE] (a typical bootstrap takes 5–15 s) and only reaches 1 when the
 * tunnel is up. The "Continue without Nym" escape appears once the wait looks stuck or bootstrap
 * failed, so a healthy start never invites the user to drop the mixnet.
 */
object NymSplashProgress {
    /** Time constant of the eased estimate; ~70% at 10 s, ~90% at 18 s. */
    private const val EASE_MS = 8_000.0

    /** The estimate never claims completion before the tunnel is up. */
    const val MAX_ESTIMATE = 0.95f

    /** Past this, the bootstrap is slower than usual and the user may skip it. */
    const val STUCK_AFTER_MS = 20_000L

    fun fraction(
        elapsedMs: Long,
        ready: Boolean,
    ): Float {
        if (ready) return 1f
        val t = elapsedMs.coerceAtLeast(0L).toDouble()
        return (MAX_ESTIMATE * (1.0 - exp(-t / EASE_MS))).toFloat()
    }

    fun isStuck(elapsedMs: Long): Boolean = elapsedMs >= STUCK_AFTER_MS

    fun showContinueWithout(
        elapsedMs: Long,
        failed: Boolean,
    ): Boolean = failed || isStuck(elapsedMs)

    fun elapsedSeconds(
        startedAtMs: Long,
        nowMs: Long,
    ): Long = if (startedAtMs <= 0L) 0L else ((nowMs - startedAtMs).coerceAtLeast(0L) / 1_000L)
}
