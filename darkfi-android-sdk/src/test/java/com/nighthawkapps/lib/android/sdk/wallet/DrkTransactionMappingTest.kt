package com.nighthawkapps.lib.android.sdk.wallet

import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.DrkTransactionRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DrkTransactionMappingTest {
    @Test
    fun maps_mined_and_pending_rows() {
        val pending =
            DrkTransactionMapping.overview(
                DrkTransactionRecord(
                    txHash = "abc",
                    status = "Broadcasted",
                    blockHeight = -1L,
                    feeAtomic = 0L,
                    isSent = true,
                    netValueAtomic = 0L,
                ),
            )
        assertEquals("abc", pending.rawId)
        assertNull(pending.minedHeight)
        assertEquals(true, pending.isSentTransaction)

        val mined =
            DrkTransactionMapping.overview(
                DrkTransactionRecord(
                    txHash = "def",
                    status = "Confirmed",
                    blockHeight = 42L,
                    feeAtomic = 5L,
                    isSent = false,
                    netValueAtomic = 100L,
                ),
            )
        assertEquals(42L, mined.minedHeight)
        assertEquals(5L, mined.totalFeeAtomic)
        assertEquals(100L, mined.netValueAtomic)
    }
}
