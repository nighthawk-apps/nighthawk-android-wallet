package com.nighthawkapps.lib.android.sdk.wallet

import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.DrkTransactionRecord

internal object DrkTransactionMapping {
    fun overview(record: DrkTransactionRecord): DarkfiTransactionOverview =
        DarkfiTransactionOverview(
            rawId = record.txHash,
            minedHeight = record.blockHeight.takeIf { it >= 0L },
            timestampEpochMillis = null,
            totalFeeAtomic = record.feeAtomic,
            isSentTransaction = record.isSent,
            netValueAtomic = record.netValueAtomic,
        )

    fun overviews(records: List<DrkTransactionRecord>): List<DarkfiTransactionOverview> = records.map(::overview)
}
