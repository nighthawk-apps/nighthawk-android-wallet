package com.nighthawkapps.lib.android.sdk.mesh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeshBulkPolicyTest {
    @Test
    fun bgWorkerNeverStartsBulk() {
        assertFalse(
            NighthawkBulkPolicy.mayStart(
                foreground = true,
                gatewayReady = true,
                fromBackgroundWorker = true,
            ),
        )
        assertTrue(
            NighthawkBulkPolicy.mayStart(
                foreground = true,
                gatewayReady = true,
                fromBackgroundWorker = false,
            ),
        )
        assertFalse(
            NighthawkBulkPolicy.mayStart(
                foreground = false,
                gatewayReady = true,
                fromBackgroundWorker = false,
            ),
        )
    }

    @Test
    fun quotasMatchRust() {
        assertEquals(200L * 1024L * 1024L, NighthawkBulkPolicy.MAX_BYTES)
        assertEquals(15L * 60L, NighthawkBulkPolicy.MAX_SECS)
        assertEquals(2, NighthawkBulkPolicy.KIND_SOFTAP)
    }
}
