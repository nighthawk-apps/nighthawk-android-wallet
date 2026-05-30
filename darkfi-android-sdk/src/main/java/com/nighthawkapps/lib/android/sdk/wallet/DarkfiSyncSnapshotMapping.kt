@file:Suppress("MaxLineLength")

package com.nighthawkapps.lib.android.sdk.wallet

import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.DrkSyncSnapshot

/** Maps UniFFI scan progress into wallet UI models. */
internal object DarkfiSyncSnapshotMapping {
    fun processorInfo(snapshot: DrkSyncSnapshot): DarkfiProcessorInfo =
        DarkfiProcessorInfo(
            scannedBlocks = snapshot.scannedBlocks,
            chainTip = snapshot.chainTip,
        )

    fun progress(snapshot: DrkSyncSnapshot): DarkfiPercent = progress(scannedBlocks = snapshot.scannedBlocks, chainTip = snapshot.chainTip)

    fun progress(
        scannedBlocks: Long,
        chainTip: Long,
    ): DarkfiPercent =
        DarkfiPercent(
            if (chainTip > 0L) {
                (scannedBlocks.toFloat() / chainTip.toFloat()).coerceIn(0f, 1f)
            } else {
                1f
            },
        )
}
