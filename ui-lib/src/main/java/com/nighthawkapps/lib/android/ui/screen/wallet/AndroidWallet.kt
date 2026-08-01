package com.nighthawkapps.lib.android.ui.screen.wallet

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboard
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nighthawkapps.lib.android.global.AppDaemonCoordinator
import com.nighthawkapps.lib.android.global.DeepLinkUtil
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiAmountFormatter
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiTransactionOverview
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.ShortcutAction
import com.nighthawkapps.lib.android.ui.common.removeTrailingZero
import com.nighthawkapps.lib.android.ui.common.setPlainText
import com.nighthawkapps.lib.android.ui.common.showMessage
import com.nighthawkapps.lib.android.ui.daemon.DarkfiRestartConnectionDialog
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.HomeViewModel
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.WalletViewModel
import com.nighthawkapps.lib.android.ui.screen.send.model.SendArgumentsWrapper
import com.nighthawkapps.lib.android.ui.screen.settings.viewmodel.SettingsViewModel
import com.nighthawkapps.lib.android.ui.screen.wallet.view.WalletView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun MainActivity.AndroidWallet(
    sendArgumentsWrapper: SendArgumentsWrapper?,
    onAddressQrCodes: () -> Unit,
    onTransactionDetail: (String) -> Unit,
    onViewTransactionHistory: () -> Unit,
    onSendFromDeepLink: () -> Unit,
    onScanToSend: () -> Unit,
) {
    WrapWallet(
        activity = this,
        sendArgumentsWrapper = sendArgumentsWrapper,
        onAddressQrCodes = onAddressQrCodes,
        onTransactionDetail = onTransactionDetail,
        onViewTransactionHistory = onViewTransactionHistory,
        onSendFromDeepLink = onSendFromDeepLink,
        onScanToSend = onScanToSend,
    )
}

@Composable
internal fun WrapWallet(
    activity: ComponentActivity,
    sendArgumentsWrapper: SendArgumentsWrapper?,
    onAddressQrCodes: () -> Unit,
    onTransactionDetail: (String) -> Unit,
    onViewTransactionHistory: () -> Unit,
    onSendFromDeepLink: () -> Unit,
    onScanToSend: () -> Unit,
) {
    val homeViewModel by activity.viewModels<HomeViewModel>()
    val walletViewModel by activity.viewModels<WalletViewModel>()
    val walletSnapshot = walletViewModel.walletSnapshot.collectAsStateWithLifecycle().value
    val transactionSnapshot =
        walletViewModel.transactionSnapshot.collectAsStateWithLifecycle().value

    val settingsViewModel by activity.viewModels<SettingsViewModel>()
    val isKeepScreenOnWhileSyncing =
        settingsViewModel.isKeepScreenOnWhileSyncing.collectAsStateWithLifecycle().value
    val clipboardManager = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()

    LaunchedEffect(key1 = Unit) {
        homeViewModel.scheduleSpotPriceRefresh()
    }

    if (null == walletSnapshot) {
        // We can show progress bar
    } else {
        LaunchedEffect(key1 = Unit) {
            launch {
                delay(500)
                homeViewModel.shortcutAction?.let {
                    when (it) {
                        ShortcutAction.SEND_MONEY_SCAN_QR_CODE -> {
                            onSendFromDeepLink()
                        }

                        ShortcutAction.RECEIVE_MONEY_QR_CODE -> {
                            onAddressQrCodes()
                            homeViewModel.shortcutAction = null
                        }
                    }
                }
                homeViewModel.intentDataUriForDeepLink?.let {
                    DeepLinkUtil.getSendDeepLinkData(it)?.let { sendDeepLinkData ->
                        homeViewModel.sendDeepLinkData = sendDeepLinkData
                        onSendFromDeepLink()
                        homeViewModel.intentDataUriForDeepLink = null
                    }
                }
                sendArgumentsWrapper?.let {
                    it.recipientAddress?.let { address ->
                        homeViewModel.sendDeepLinkData =
                            DeepLinkUtil.SendDeepLinkData(
                                address = address,
                                amount = null,
                                memo = it.memo,
                            )
                        onSendFromDeepLink()
                    }
                }
            }
            if (homeViewModel.isAnyExpectingTransaction(walletSnapshot)) {
                activity.showMessage(
                    activity.getString(
                        R.string.ns_expecting_balance_snack_bar_msg,
                        DarkfiAmountFormatter.formatAtomic(homeViewModel.expectingPendingAtomic).removeTrailingZero(),
                    ),
                )
            }
        }

        val onItemLongClickAction: (DarkfiTransactionOverview) -> Unit = {
            clipboardScope.launch {
                clipboardManager.setPlainText(it.rawId)
            }
            activity.showMessage(activity.getString(R.string.transaction_id_copied))
        }

        val fiatCurrencyUiState by homeViewModel.fiatCurrencyUiStateFlow.collectAsStateWithLifecycle()
        val isFiatCurrencyPreferred by homeViewModel.isFiatCurrencyPreferredOverNative.collectAsStateWithLifecycle()
        val isBandit = walletViewModel.isBandit.collectAsStateWithLifecycle().value
        settingsViewModel.setBanditStatus(isBandit)

        val daemonStatus by AppDaemonCoordinator.get().status.collectAsStateWithLifecycle()
        var showRestartDialog by remember { mutableStateOf(false) }

        if (showRestartDialog) {
            DarkfiRestartConnectionDialog(
                onConfirm = { AppDaemonCoordinator.get().restartConnection() },
                onDismiss = { showRestartDialog = false },
            )
        }

        WalletView(
            walletSnapshot = walletSnapshot,
            transactionSnapshot = transactionSnapshot,
            isKeepScreenOnWhileSyncing = isKeepScreenOnWhileSyncing,
            isFiatCurrencyPreferred = isFiatCurrencyPreferred,
            fiatCurrencyUiState = fiatCurrencyUiState,
            isBandit = isBandit,
            onAddressQrCodes = onAddressQrCodes,
            onTransactionDetail = onTransactionDetail,
            onViewTransactionHistory = onViewTransactionHistory,
            onLongItemClick = onItemLongClickAction,
            onFlipCurrency = homeViewModel::onPreferredCurrencyChanged,
            onScanToSend = onScanToSend,
            daemonStatus = daemonStatus,
            onDaemonStatusClick = { showRestartDialog = true },
        )
    }
    activity.reportFullyDrawn()
}
