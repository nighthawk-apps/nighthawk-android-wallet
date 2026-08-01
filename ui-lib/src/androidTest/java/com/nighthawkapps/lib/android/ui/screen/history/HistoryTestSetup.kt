package com.nighthawkapps.lib.android.ui.screen.history

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme
import com.nighthawkapps.lib.android.ui.screen.history.state.TransactionHistorySyncState
import com.nighthawkapps.lib.android.ui.screen.history.view.History
import java.util.concurrent.atomic.AtomicInteger

class HistoryTestSetup(
    private val composeTestRule: ComposeContentTestRule,
    initialHistorySyncState: TransactionHistorySyncState
) {
    private val onBackCount = AtomicInteger(0)

    fun getOnBackCount(): Int {
        composeTestRule.waitForIdle()
        return onBackCount.get()
    }

    init {
        composeTestRule.setContent {
            WalletTheme {
                History(
                    transactionState = initialHistorySyncState,
                    goBack = {
                        onBackCount.incrementAndGet()
                    }
                )
            }
        }
    }
}
