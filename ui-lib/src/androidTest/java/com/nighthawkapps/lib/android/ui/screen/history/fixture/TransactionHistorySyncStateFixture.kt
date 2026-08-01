package com.nighthawkapps.lib.android.ui.screen.history.fixture

import com.nighthawkapps.lib.android.sdk.wallet.DarkfiTransactionOverview
import com.nighthawkapps.lib.android.ui.fixture.DarkfiTransactionOverviewFixture
import com.nighthawkapps.lib.android.ui.screen.history.state.TransactionHistorySyncState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

internal object TransactionHistorySyncStateFixture {
    val TRANSACTIONS =
        persistentListOf(
            DarkfiTransactionOverviewFixture.new(rawId = "fixture_tx_0"),
            DarkfiTransactionOverviewFixture.new(rawId = "fixture_tx_1"),
            DarkfiTransactionOverviewFixture.new(rawId = "fixture_tx_2"),
        )
    val STATE = TransactionHistorySyncState.Syncing(TRANSACTIONS)

    fun new(
        transactions: ImmutableList<DarkfiTransactionOverview> = TRANSACTIONS,
        state: TransactionHistorySyncState = STATE,
    ) = when (state) {
        is TransactionHistorySyncState.Syncing -> state.copy(transactions)
        is TransactionHistorySyncState.Done -> state.copy(transactions)
        TransactionHistorySyncState.Loading -> state
    }
}
