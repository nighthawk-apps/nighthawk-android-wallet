package com.nighthawkapps.lib.android.ui.splash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NymSplashProgressTest {
    @Test
    fun fraction_startsAtZero_andIsMonotonic() {
        assertEquals(0f, NymSplashProgress.fraction(0, ready = false), 0f)
        var last = 0f
        for (ms in 0L..120_000L step 1_000L) {
            val f = NymSplashProgress.fraction(ms, ready = false)
            assertTrue("fraction regressed at $ms ms", f >= last)
            last = f
        }
    }

    @Test
    fun fraction_neverClaimsCompletionUntilReady() {
        assertTrue(NymSplashProgress.fraction(10 * 60_000L, ready = false) <= NymSplashProgress.MAX_ESTIMATE)
        assertEquals(1f, NymSplashProgress.fraction(1_000L, ready = true), 0f)
    }

    @Test
    fun fraction_tracksTypicalBootstrap() {
        val at10s = NymSplashProgress.fraction(10_000L, ready = false)
        assertTrue("10 s should look well under way: $at10s", at10s in 0.5f..0.8f)
    }

    @Test
    fun continueWithout_hiddenWhileHealthy_shownWhenStuckOrFailed() {
        assertFalse(NymSplashProgress.showContinueWithout(5_000L, failed = false))
        assertFalse(NymSplashProgress.showContinueWithout(NymSplashProgress.STUCK_AFTER_MS - 1, failed = false))
        assertTrue(NymSplashProgress.showContinueWithout(NymSplashProgress.STUCK_AFTER_MS, failed = false))
        assertTrue(NymSplashProgress.showContinueWithout(1_000L, failed = true))
    }

    @Test
    fun elapsedSeconds_handlesUnsetAndClockSkew() {
        assertEquals(0L, NymSplashProgress.elapsedSeconds(0L, 5_000L))
        assertEquals(0L, NymSplashProgress.elapsedSeconds(10_000L, 5_000L))
        assertEquals(12L, NymSplashProgress.elapsedSeconds(1_000L, 13_500L))
    }
}
