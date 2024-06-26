package co.electriccoin.zcash.ui.screen.transactionhistory

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cash.z.ecc.android.sdk.model.TransactionOverview
import co.electriccoin.zcash.ui.MainActivity
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.showMessage
import co.electriccoin.zcash.ui.common.toFormattedString
import co.electriccoin.zcash.ui.screen.home.viewmodel.HomeViewModel
import co.electriccoin.zcash.ui.screen.home.viewmodel.WalletViewModel
import co.electriccoin.zcash.ui.screen.transactionhistory.view.TransactionHistory

@Composable
internal fun MainActivity.AndroidTransactionHistory(onBack: () -> Unit, onTransactionDetail: (String) -> Unit) {
    WrapTransactionHistory(activity = this, onBack = onBack, onTransactionDetail = onTransactionDetail)
}

@Composable
internal fun WrapTransactionHistory(activity: ComponentActivity, onBack: () -> Unit, onTransactionDetail: (String) -> Unit) {
    val walletViewModel by activity.viewModels<WalletViewModel>()
    val homeViewModel by activity.viewModels<HomeViewModel>()
    val clipboardManager = LocalClipboardManager.current
    val onItemLongClickAction: (TransactionOverview) -> Unit = {
        clipboardManager.setText(AnnotatedString(it.rawId.byteArray.toFormattedString()))
        activity.showMessage(activity.getString(R.string.transaction_id_copied))
    }
    val transactionSnapshot =
        walletViewModel.transactionSnapshot.collectAsStateWithLifecycle().value
    val fiatCurrencyUiState by homeViewModel.fiatCurrencyUiStateFlow.collectAsStateWithLifecycle()
    val isFiatCurrencyPreferred by homeViewModel.isFiatCurrencyPreferredOverZec.collectAsStateWithLifecycle()
    TransactionHistory(
        transactionSnapshot = transactionSnapshot,
        fiatCurrencyUiState = fiatCurrencyUiState,
        isFiatCurrencyPreferred = isFiatCurrencyPreferred,
        onBack = onBack,
        onTransactionDetail = onTransactionDetail,
        onItemLongClick = onItemLongClickAction
    )
}
