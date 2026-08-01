package com.nighthawkapps.lib.android.ui.screen.history

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nighthawkapps.lib.android.spackle.Twig
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.screen.history.view.History
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.WalletViewModel

@Composable
internal fun MainActivity.WrapHistory(goBack: () -> Unit) {
    WrapHistory(
        activity = this,
        goBack = goBack
    )
}

@Composable
internal fun WrapHistory(
    activity: ComponentActivity,
    goBack: () -> Unit
) {
    val walletViewModel by activity.viewModels<WalletViewModel>()

    val transactionHistoryState =
        walletViewModel.transactionHistoryState.collectAsStateWithLifecycle().value

    Twig.debug { "Current transaction history state: $transactionHistoryState" }

    History(
        transactionState = transactionHistoryState,
        goBack = goBack
    )
}
