package com.nighthawkapps.lib.android.sdk.mesh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeshForegroundRetryTest {
    @Test
    fun retriesThreeTimesThenStops() {
        assertTrue(MeshForegroundRetry.shouldRetry(0))
        assertTrue(MeshForegroundRetry.shouldRetry(2))
        assertFalse(MeshForegroundRetry.shouldRetry(3))
        assertEquals(1_000L, MeshForegroundRetry.delayMs(1))
        assertEquals(3_000L, MeshForegroundRetry.delayMs(3))
    }
}
