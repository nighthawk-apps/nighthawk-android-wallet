package com.nighthawkapps.lib.android.sdk.mesh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeshScanRecoveryTest {
    @Test
    fun featureUnsupportedDoesNotRetry() {
        assertFalse(MeshScanRecovery.shouldRetry(MeshScanRecovery.FEATURE_UNSUPPORTED))
    }

    @Test
    fun transientScanFailuresRetryWithCappedBackoff() {
        assertTrue(MeshScanRecovery.shouldRetry(MeshScanRecovery.ALREADY_STARTED))
        assertTrue(MeshScanRecovery.shouldRetry(MeshScanRecovery.REGISTRATION_FAILED))
        assertTrue(MeshScanRecovery.shouldRetry(MeshScanRecovery.INTERNAL_ERROR))
        assertEquals(3_000L, MeshScanRecovery.delayMs(MeshScanRecovery.ALREADY_STARTED, 1))
        assertEquals(9_000L, MeshScanRecovery.delayMs(MeshScanRecovery.OUT_OF_HARDWARE_RESOURCES, 1))
        assertEquals(10_000L, MeshScanRecovery.delayMs(MeshScanRecovery.SCANNING_TOO_FREQUENTLY, 1))
        assertEquals(30_000L, MeshScanRecovery.delayMs(MeshScanRecovery.ALREADY_STARTED, 20))
    }
}
