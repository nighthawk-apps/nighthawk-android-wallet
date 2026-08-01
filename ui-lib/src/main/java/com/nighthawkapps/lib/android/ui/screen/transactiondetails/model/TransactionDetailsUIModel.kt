package com.nighthawkapps.lib.android.ui.screen.transactiondetails.model

import com.nighthawkapps.lib.android.sdk.wallet.DarkfiNetwork
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiTransactionOverview
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiTransactionRecipient

data class TransactionDetailsUIModel(
    val transactionOverview: DarkfiTransactionOverview?,
    val transactionRecipient: DarkfiTransactionRecipient?,
    val network: DarkfiNetwork?,
    val networkHeight: Long?,
    val memo: String = "",
)

enum class DarkfiTransactionUiState {
    Confirmed,
    Pending,
    Expired,
}

val DarkfiTransactionOverview.uiTransactionState: DarkfiTransactionUiState
    get() =
        when {
            minedHeight != null -> DarkfiTransactionUiState.Confirmed
            timestampEpochMillis == null -> DarkfiTransactionUiState.Pending
            else -> DarkfiTransactionUiState.Pending
        }

val DarkfiTransactionOverview.blockTimeEpochSeconds: Long?
    get() = timestampEpochMillis?.div(1000)
