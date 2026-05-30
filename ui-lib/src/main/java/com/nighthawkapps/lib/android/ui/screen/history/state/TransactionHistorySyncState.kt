package com.nighthawkapps.lib.android.ui.screen.history.state

import com.nighthawkapps.lib.android.sdk.wallet.DarkfiTransactionOverview
import kotlinx.collections.immutable.ImmutableList

sealed class TransactionHistorySyncState {
    data object Loading : TransactionHistorySyncState() {
        override fun toString() = "Loading" // NON-NLS
    }

    data class Syncing(
        val transactions: ImmutableList<DarkfiTransactionOverview>
    ) : TransactionHistorySyncState() {
        fun hasNoTransactions(): Boolean = transactions.isEmpty()
    }

    data class Done(
        val transactions: ImmutableList<DarkfiTransactionOverview>
    ) : TransactionHistorySyncState() {
        fun hasNoTransactions(): Boolean = transactions.isEmpty()
    }
}
