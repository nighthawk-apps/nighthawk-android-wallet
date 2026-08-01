package com.nighthawkapps.lib.android.sdk.wallet

import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.DrkSyncSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DarkfiSyncSnapshotMappingTest {
    @Test
    fun progress_atHalfChainTip() {
        val percent =
            DarkfiSyncSnapshotMapping.progress(
                DrkSyncSnapshot(scannedBlocks = 500L, chainTip = 1000L),
            )
        assertEquals(0.5f, percent.decimal, 1e-4f)
    }

    @Test
    fun progress_whenChainTipZero_isComplete() {
        val percent =
            DarkfiSyncSnapshotMapping.progress(
                DrkSyncSnapshot(scannedBlocks = 0L, chainTip = 0L),
            )
        assertEquals(1f, percent.decimal, 1e-4f)
    }

    @Test
    fun progress_clampsAboveOne() {
        val percent =
            DarkfiSyncSnapshotMapping.progress(
                scannedBlocks = 2000L,
                chainTip = 1000L,
            )
        assertEquals(1f, percent.decimal, 1e-4f)
    }

    @Test
    fun processorInfo_copiesSnapshotFields() {
        val info =
            DarkfiSyncSnapshotMapping.processorInfo(
                DrkSyncSnapshot(scannedBlocks = 42L, chainTip = 100L),
            )
        assertEquals(42L, info.scannedBlocks)
        assertEquals(100L, info.chainTip)
    }

    @Test
    fun progress_atZero_isZero() {
        val percent =
            DarkfiSyncSnapshotMapping.progress(
                scannedBlocks = 0L,
                chainTip = 1000L,
            )
        assertTrue(percent.decimal < 0.01f)
    }
}
