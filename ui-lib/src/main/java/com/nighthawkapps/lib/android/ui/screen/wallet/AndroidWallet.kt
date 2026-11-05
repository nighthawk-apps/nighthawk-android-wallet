package com.nighthawkapps.lib.android.ui.screen.wallet

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nighthawkapps.lib.android.global.AppDaemonCoordinator
import com.nighthawkapps.lib.android.global.DeepLinkUtil
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiAmountFormatter
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiAmountParser
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiTransactionOverview
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.ShortcutAction
import com.nighthawkapps.lib.android.ui.common.removeTrailingZero
import com.nighthawkapps.lib.android.ui.common.setPlainText
import com.nighthawkapps.lib.android.ui.common.showMessage
import com.nighthawkapps.lib.android.ui.daemon.DarkfiRestartConnectionDialog
import com.nighthawkapps.lib.android.ui.preference.StandardPreferenceKeys
import com.nighthawkapps.lib.android.ui.preference.StandardPreferenceSingleton
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.HomeViewModel
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.WalletViewModel
import com.nighthawkapps.lib.android.ui.screen.navigation.NighthawkExpandedWidth
import com.nighthawkapps.lib.android.ui.screen.send.model.SendArgumentsWrapper
import com.nighthawkapps.lib.android.ui.screen.settings.viewmodel.SettingsViewModel
import com.nighthawkapps.lib.android.ui.screen.wallet.view.TransactionDetailAndNextSteps
import com.nighthawkapps.lib.android.ui.screen.wallet.view.WalletCompanionHub
import com.nighthawkapps.lib.android.ui.screen.wallet.view.WalletView
import com.nighthawkapps.lib.android.ui.screen.wallet.view.amountExcludingFeeAtomic
import com.nighthawkapps.lib.android.ui.screen.wallet.view.isSendDisabled
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
    onSendToken: (String) -> Unit = {},
    onSendMoney: () -> Unit = {},
    onReceiveMoney: () -> Unit = {},
    onRequestMoney: () -> Unit = {},
) {
    WrapWallet(
        activity = this,
        sendArgumentsWrapper = sendArgumentsWrapper,
        onAddressQrCodes = onAddressQrCodes,
        onTransactionDetail = onTransactionDetail,
        onViewTransactionHistory = onViewTransactionHistory,
        onSendFromDeepLink = onSendFromDeepLink,
        onScanToSend = onScanToSend,
        onSendToken = onSendToken,
        onSendMoney = onSendMoney,
        onReceiveMoney = onReceiveMoney,
        onRequestMoney = onRequestMoney,
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
    onSendToken: (String) -> Unit = {},
    onSendMoney: () -> Unit = {},
    onReceiveMoney: () -> Unit = {},
    onRequestMoney: () -> Unit = {},
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
    val context = LocalContext.current
    var meshOn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val prefs = StandardPreferenceSingleton.getInstance(context.applicationContext)
        meshOn = StandardPreferenceKeys.IS_NIGHTHAWK_MESH_ENABLED.getValue(prefs)
        StandardPreferenceKeys.IS_NIGHTHAWK_MESH_ENABLED.observe(prefs).collect { meshOn = it }
    }

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
                                amount = it.amount?.let { display -> DarkfiAmountParser.parseDisplayAmountToAtomic(display) },
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
        LaunchedEffect(isBandit) {
            settingsViewModel.setBanditStatus(isBandit)
        }

        val daemonStatus by AppDaemonCoordinator.get().status.collectAsStateWithLifecycle()
        val synchronizer by walletViewModel.synchronizer.collectAsStateWithLifecycle()
        val tokenBalances by (
            synchronizer?.tokenBalances
                ?: kotlinx.coroutines.flow.flowOf(emptyList())
        ).collectAsStateWithLifecycle(emptyList())
        var showRestartDialog by remember { mutableStateOf(false) }
        val addresses by walletViewModel.addresses.collectAsStateWithLifecycle()
        val receiveAddress = addresses?.privateAddresses?.firstOrNull().orEmpty()
        var selectedTransactionId by rememberSaveable { mutableStateOf<String?>(null) }

        if (showRestartDialog) {
            DarkfiRestartConnectionDialog(
                onConfirm = { AppDaemonCoordinator.get().restartConnection() },
                onDismiss = { showRestartDialog = false },
            )
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val expanded = maxWidth >= NighthawkExpandedWidth
            val selectedTransaction =
                transactionSnapshot.firstOrNull { it.rawId == selectedTransactionId }
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                ) {
                    WalletView(
                        walletSnapshot = walletSnapshot,
                        transactionSnapshot = transactionSnapshot,
                        isKeepScreenOnWhileSyncing = isKeepScreenOnWhileSyncing,
                        isFiatCurrencyPreferred = isFiatCurrencyPreferred,
                        fiatCurrencyUiState = fiatCurrencyUiState,
                        onAddressQrCodes = onAddressQrCodes,
                        onTransactionDetail = { id ->
                            if (expanded) {
                                selectedTransactionId = id
                            } else {
                                onTransactionDetail(id)
                            }
                        },
                        onViewTransactionHistory = onViewTransactionHistory,
                        onLongItemClick = onItemLongClickAction,
                        onFlipCurrency = homeViewModel::onPreferredCurrencyChanged,
                        onScanToSend = onScanToSend,
                        daemonStatus = daemonStatus,
                        onDaemonStatusClick = { showRestartDialog = true },
                        tokenBalances = tokenBalances,
                        onTokenClick = onSendToken,
                        meshOn = meshOn,
                        onSendMoney = onSendMoney,
                        onReceiveMoney = onReceiveMoney,
                        onRequestMoney = onRequestMoney,
                        sendDisabled = isSendDisabled(walletSnapshot.status),
                    )
                }
                if (expanded) {
                    Box(
                        modifier =
                            Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                    ) {
                        if (selectedTransaction == null) {
                            WalletCompanionHub(
                                walletSnapshot = walletSnapshot,
                                receiveAddress = receiveAddress,
                                onCopyAddress = {
                                    if (receiveAddress.isNotBlank()) {
                                        clipboardScope.launch {
                                            clipboardManager.setPlainText(receiveAddress)
                                        }
                                        activity.showMessage(activity.getString(R.string.ns_copied))
                                    }
                                },
                            )
                        } else {
                            TransactionDetailAndNextSteps(
                                transaction = selectedTransaction,
                                onCopyTxId = {
                                    clipboardScope.launch {
                                        clipboardManager.setPlainText(selectedTransaction.rawId)
                                    }
                                    activity.showMessage(activity.getString(R.string.transaction_id_copied))
                                },
                                onSendAgain = {
                                    val address = selectedTransaction.recipientAddress
                                    if (selectedTransaction.isSentTransaction && !address.isNullOrBlank()) {
                                        val amountAtomic =
                                            selectedTransaction.amountExcludingFeeAtomic().takeIf { atomic ->
                                                atomic > 0L
                                            }
                                        homeViewModel.sendDeepLinkData =
                                            DeepLinkUtil.SendDeepLinkData(
                                                address = address,
                                                amount = amountAtomic,
                                                memo = null,
                                            )
                                        onSendFromDeepLink()
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
    activity.reportFullyDrawn()
}
