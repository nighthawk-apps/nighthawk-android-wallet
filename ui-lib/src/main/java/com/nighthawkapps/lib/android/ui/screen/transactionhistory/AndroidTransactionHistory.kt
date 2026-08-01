package com.nighthawkapps.lib.android.ui.screen.transactionhistory

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiTransactionOverview
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.setPlainText
import com.nighthawkapps.lib.android.ui.common.showMessage
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.HomeViewModel
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.WalletViewModel
import com.nighthawkapps.lib.android.ui.screen.transactionhistory.view.TransactionHistory
import kotlinx.coroutines.launch

@Composable
internal fun MainActivity.AndroidTransactionHistory(
    onBack: () -> Unit,
    onTransactionDetail: (String) -> Unit
) {
    WrapTransactionHistory(activity = this, onBack = onBack, onTransactionDetail = onTransactionDetail)
}

@Composable
internal fun WrapTransactionHistory(
    activity: ComponentActivity,
    onBack: () -> Unit,
    onTransactionDetail: (String) -> Unit
) {
    val walletViewModel by activity.viewModels<WalletViewModel>()
    val homeViewModel by activity.viewModels<HomeViewModel>()
    val clipboardManager = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
    val onItemLongClickAction: (DarkfiTransactionOverview) -> Unit = {
        clipboardScope.launch {
            clipboardManager.setPlainText(it.rawId)
        }
        activity.showMessage(activity.getString(R.string.transaction_id_copied))
    }
    val transactionSnapshot =
        walletViewModel.transactionSnapshot.collectAsStateWithLifecycle().value
    val fiatCurrencyUiState by homeViewModel.fiatCurrencyUiStateFlow.collectAsStateWithLifecycle()
    val isFiatCurrencyPreferred by homeViewModel.isFiatCurrencyPreferredOverNative.collectAsStateWithLifecycle()
    TransactionHistory(
        transactionSnapshot = transactionSnapshot,
        fiatCurrencyUiState = fiatCurrencyUiState,
        isFiatCurrencyPreferred = isFiatCurrencyPreferred,
        onBack = onBack,
        onTransactionDetail = onTransactionDetail,
        onItemLongClick = onItemLongClickAction
    )
}
