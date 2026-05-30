package com.nighthawkapps.lib.android.ui.fixture

import com.nighthawkapps.lib.android.sdk.wallet.DarkfiTransactionOverview

object DarkfiTransactionOverviewFixture {
    fun new(
        rawId: String = "preview_tx_${System.nanoTime()}",
        minedHeight: Long? = 1L,
        timestampEpochMillis: Long? = System.currentTimeMillis(),
        totalFeeAtomic: Long = 1_000L,
        isSentTransaction: Boolean = false,
        netValueAtomic: Long = 50_000_000L,
    ): DarkfiTransactionOverview =
        DarkfiTransactionOverview(
            rawId = rawId,
            minedHeight = minedHeight,
            timestampEpochMillis = timestampEpochMillis,
            totalFeeAtomic = totalFeeAtomic,
            isSentTransaction = isSentTransaction,
            netValueAtomic = netValueAtomic,
        )
}
