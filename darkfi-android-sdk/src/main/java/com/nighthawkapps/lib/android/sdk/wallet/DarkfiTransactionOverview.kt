package com.nighthawkapps.lib.android.sdk.wallet

data class DarkfiTransactionOverview(
    val rawId: String,
    val minedHeight: Long?,
    val timestampEpochMillis: Long?,
    val totalFeeAtomic: Long,
    val isSentTransaction: Boolean,
    val netValueAtomic: Long,
)
