package com.nighthawkapps.lib.android.sdk.wallet

data class DarkfiTransactionOverview(
    val rawId: String,
    val minedHeight: Long?,
    val timestampEpochMillis: Long?,
    val totalFeeAtomic: Long,
    val isSentTransaction: Boolean,
    val netValueAtomic: Long,
    val contractSummary: String = "",
    val recipientAddress: String? = null,
    /** How this transaction was discovered/built (from Rust `SyncMethod`). */
    val syncMethod: DarkfiSyncMethod = DarkfiSyncMethod.UNKNOWN,
)
