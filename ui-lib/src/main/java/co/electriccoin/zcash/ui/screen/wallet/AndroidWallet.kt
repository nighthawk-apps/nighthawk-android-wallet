package co.electriccoin.zcash.ui.screen.wallet

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cash.z.ecc.android.sdk.model.TransactionOverview
import co.electriccoin.zcash.global.DeepLinkUtil
import co.electriccoin.zcash.ui.MainActivity
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.showMessage
import co.electriccoin.zcash.ui.common.toFormattedString
import co.electriccoin.zcash.ui.configuration.ConfigurationEntries
import co.electriccoin.zcash.ui.configuration.RemoteConfig
import co.electriccoin.zcash.ui.screen.home.viewmodel.HomeViewModel
import co.electriccoin.zcash.ui.screen.home.viewmodel.WalletViewModel
import co.electriccoin.zcash.ui.screen.settings.viewmodel.SettingsViewModel
import co.electriccoin.zcash.ui.screen.wallet.view.WalletView
import co.electriccoin.zcash.ui.screen.wallet.view.isSyncing

@Composable
internal fun MainActivity.AndroidWallet(
    onAddressQrCodes: () -> Unit,
    onShieldNow: () -> Unit,
    onTransactionDetail: (Long) -> Unit,
    onViewTransactionHistory: () -> Unit,
    onSendFromDeepLink: () -> Unit
) {
    WrapWallet(
        activity = this,
        onAddressQrCodes = onAddressQrCodes,
        onShieldNow = onShieldNow,
        onTransactionDetail = onTransactionDetail,
        onViewTransactionHistory = onViewTransactionHistory,
        onSendFromDeepLink = onSendFromDeepLink
    )
}

@Composable
internal fun WrapWallet(
    activity: ComponentActivity,
    onAddressQrCodes: () -> Unit,
    onShieldNow: () -> Unit,
    onTransactionDetail: (Long) -> Unit,
    onViewTransactionHistory: () -> Unit,
    onSendFromDeepLink: () -> Unit
) {
    val homeViewModel by activity.viewModels<HomeViewModel>()
    val walletViewModel by activity.viewModels<WalletViewModel>()
    val walletSnapshot = walletViewModel.walletSnapshot.collectAsStateWithLifecycle().value
    val transactionSnapshot = walletViewModel.transactionSnapshot.collectAsStateWithLifecycle().value

    val settingsViewModel by activity.viewModels<SettingsViewModel>()
    val isKeepScreenOnWhileSyncing =
        settingsViewModel.isKeepScreenOnWhileSyncing.collectAsStateWithLifecycle().value
    val isFiatConversionEnabled = ConfigurationEntries.IS_FIAT_CONVERSION_ENABLED.getValue(RemoteConfig.current)
    val clipboardManager = LocalClipboardManager.current

    if (null == walletSnapshot) {
        // We can show progress bar
    } else {
        val isSyncing = isSyncing(walletSnapshot.status)
        LaunchedEffect(key1 = isSyncing) {
            homeViewModel.onTransferTabStateChanged(enable = isSyncing.not())

            if (isSyncing.not()) {
                homeViewModel.intentDataUriForDeepLink?.let {
                    DeepLinkUtil.getSendDeepLinkData(it)?.let { sendDeepLinkData ->
                        homeViewModel.sendDeepLinkData = sendDeepLinkData
                        onSendFromDeepLink()
                        homeViewModel.intentDataUriForDeepLink = null
                    }
                }
            }
        }
        val onItemLongClickAction: (TransactionOverview) -> Unit = {
            clipboardManager.setText(AnnotatedString(it.rawId.byteArray.toFormattedString()))
            activity.showMessage(activity.getString(R.string.transaction_id_copied))
        }
        WalletView(
            walletSnapshot = walletSnapshot,
            transactionSnapshot = transactionSnapshot,
            isKeepScreenOnWhileSyncing = isKeepScreenOnWhileSyncing,
            isFiatConversionEnabled = isFiatConversionEnabled,
            onShieldNow = onShieldNow,
            onAddressQrCodes = onAddressQrCodes,
            onTransactionDetail = onTransactionDetail,
            onViewTransactionHistory = onViewTransactionHistory,
            onLongItemClick = onItemLongClickAction
        )
    }
    activity.reportFullyDrawn()
}
