package co.electriccoin.zcash.ui.screen.send.nighthawk

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.z.ecc.android.sdk.SdkSynchronizer
import cash.z.ecc.android.sdk.ext.convertZatoshiToZecString
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.ZecSendExt
import cash.z.ecc.android.sdk.type.AddressType
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.MainActivity
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.ShortcutAction
import co.electriccoin.zcash.ui.common.showMessage
import co.electriccoin.zcash.ui.screen.home.viewmodel.HomeViewModel
import co.electriccoin.zcash.ui.screen.home.viewmodel.WalletViewModel
import co.electriccoin.zcash.ui.screen.navigation.BottomNavItem
import co.electriccoin.zcash.ui.screen.send.ext.ABBREVIATION_INDEX
import co.electriccoin.zcash.ui.screen.send.model.SendArgumentsWrapper
import co.electriccoin.zcash.ui.screen.send.nighthawk.model.SendConfirmationState
import co.electriccoin.zcash.ui.screen.send.nighthawk.model.SendUIState
import co.electriccoin.zcash.ui.screen.send.nighthawk.model.SubmitResult
import co.electriccoin.zcash.ui.screen.send.nighthawk.view.EnterMessage
import co.electriccoin.zcash.ui.screen.send.nighthawk.view.EnterReceiverAddress
import co.electriccoin.zcash.ui.screen.send.nighthawk.view.EnterZec
import co.electriccoin.zcash.ui.screen.send.nighthawk.view.ReviewAndSend
import co.electriccoin.zcash.ui.screen.send.nighthawk.view.SendConfirmation
import co.electriccoin.zcash.ui.screen.send.nighthawk.viewmodel.CreateTransactionsViewModel
import co.electriccoin.zcash.ui.screen.send.nighthawk.viewmodel.SendViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun MainActivity.AndroidSend(
    onBack: () -> Unit,
    onTopUpWallet: () -> Unit,
    navigateTo: (String) -> Unit,
    /*onMoreDetails: (String) -> Unit,*/
    onScan: () -> Unit,
    sendArgumentsWrapper: SendArgumentsWrapper? = null
) {
    WrapAndroidSend(
        activity = this,
        onBack = onBack,
        onTopUpWallet = onTopUpWallet,
        navigateTo = navigateTo,
        /*onMoreDetails = onMoreDetails,*/
        onScan = onScan,
        sendArgumentsWrapper = sendArgumentsWrapper
    )
}

@Composable
internal fun WrapAndroidSend(
    activity: ComponentActivity,
    onBack: () -> Unit,
    onTopUpWallet: () -> Unit,
    navigateTo: (String) -> Unit,
    /*onMoreDetails: (String) -> Unit,*/
    onScan: () -> Unit,
    sendArgumentsWrapper: SendArgumentsWrapper? = null
) {
    val homeViewModel by activity.viewModels<HomeViewModel>()
    val sendViewModel by activity.viewModels<SendViewModel>()
    val walletViewModel by activity.viewModels<WalletViewModel>()
    val createTransactionsViewModel = viewModel<CreateTransactionsViewModel>()
    val activityScope = rememberCoroutineScope()


    val fiatCurrencyUiState by homeViewModel.fiatCurrencyUiStateFlow.collectAsStateWithLifecycle()
    val isFiatCurrencyPreferred by homeViewModel.isFiatCurrencyPreferredOverZec.collectAsStateWithLifecycle()
    LaunchedEffect(key1 = fiatCurrencyUiState, key2 = isFiatCurrencyPreferred) {
        sendViewModel.updateFiatCurrencyData(fiatCurrencyUiState, isFiatCurrencyPreferred)

        // Check for deepLink data if there is any. If we found then update receiverAddress, amount and memo
        launch {
            delay(1000)
            homeViewModel.sendDeepLinkData?.let {
                sendViewModel.updateReceiverAddress(it.address)
                it.amount?.let { zatoshi -> sendViewModel.enteredZecFromDeepLink(Zatoshi(zatoshi).convertZatoshiToZecString()) }
                it.memo?.let { memo -> sendViewModel.updateMemo(memo) }
            }?.also { homeViewModel.sendDeepLinkData = null }
        }
    }

    val sendUIState = sendViewModel.currentSendUIState.collectAsStateWithLifecycle()
    BackHandler(enabled = sendUIState.value != SendUIState.ENTER_ZEC) {
        Twig.debug { "WrapAndroidSend BackHandler: sendUIState $sendUIState" }
    }
    DisposableEffect(key1 = Unit) {

        // Check if there is any shortcut click data available or not
        homeViewModel.shortcutAction?.let {
            if (it == ShortcutAction.SEND_MONEY_SCAN_QR_CODE) {
                onScan()
            }
        }?.also { homeViewModel.shortcutAction = null }

        // Get data after scan the QR code and update sendViewModel receiver address
        sendArgumentsWrapper?.let {
            it.recipientAddress?.let { address ->
                sendViewModel.updateReceiverAddress(address)
            }
        }

        onDispose {
            Twig.debug { "WrapAndroidSend: onDispose $sendUIState" }
        }
    }

    when (sendUIState.value) {
        SendUIState.ENTER_ZEC -> {
            walletViewModel.walletSnapshot.collectAsStateWithLifecycle().value?.let(sendViewModel::updateEnterZecUiStateWithWalletSnapshot)
            val enterZecUIState = sendViewModel.enterZecUIState.collectAsStateWithLifecycle()
            EnterZec(
                enterZecUIState = enterZecUIState.value,
                onBack = onBack,
                onScanPaymentCode = onScan,
                onContinue = sendViewModel::onNextSendUiState,
                onTopUpWallet = onTopUpWallet,
                onKeyPressed = sendViewModel::onKeyPressed,
                onSendAllClicked = sendViewModel::onSendAllClicked,
                onFlipCurrency = {
                    sendViewModel.updateFiatCurrencyData(fiatCurrencyUiState, isFiatCurrencyPreferred.not())
                    homeViewModel.onPreferredCurrencyChanged(isFiatCurrencyPreferred.not())
                    sendViewModel.switchEnteredAmountType()
                }
            )
        }

        SendUIState.ENTER_MESSAGE -> {
            EnterMessage(
                memo = sendViewModel.userEnteredMemo,
                onBack = sendViewModel::onPreviousSendUiState,
                onContinue = sendViewModel::onEnterMessageContinue
            )
        }

        SendUIState.ENTER_ADDRESS -> {
            val scope = rememberCoroutineScope()
            val synchronizer = walletViewModel.synchronizer.collectAsStateWithLifecycle().value
            var isContinueEnabled by remember {
                mutableStateOf(false)
            }
            var validateAddressJob: Job? = null

            fun validateAddress(address: String) {
                if (address.isBlank()) {
                    isContinueEnabled = false
                    return
                }
                validateAddressJob?.let {
                    if (it.isCompleted.not() && it.isCancelled.not()) {
                        it.cancel()
                    }
                }
                validateAddressJob = scope.launch(Dispatchers.IO) {
                    synchronizer?.let {
                        val addressType = sendViewModel.validateAddress(address, it)
                        isContinueEnabled = if (addressType is AddressType.Tex || addressType.isNotValid) {
                            Twig.error { "Error in address validation, Address type is TEX which is not supported" }
                            withContext(Dispatchers.Main) {
                                activity.showMessage(activity.getString(R.string.tex_address_support_error))
                            }
                            false
                        } else {
                            true
                        }
                    }
                }
            }

            LaunchedEffect(key1 = Unit) {
                validateAddress(sendViewModel.receiverAddress)
            }

            EnterReceiverAddress(
                receiverAddress = sendArgumentsWrapper?.recipientAddress ?: sendViewModel.receiverAddress,
                isContinueBtnEnabled = isContinueEnabled,
                onBack = sendViewModel::onPreviousSendUiState,
                onScan = onScan,
                onValueChanged = { address ->
                    if (address.length <= ABBREVIATION_INDEX) {
                        isContinueEnabled = false
                        return@EnterReceiverAddress
                    }
                    validateAddress(address)
                },
                onContinue = {
                    val zecSendValidation = ZecSendExt.new(
                        activity,
                        it,
                        sendViewModel.getEnteredAmountInZecString(),
                        sendViewModel.userEnteredMemo
                    )

                    when (zecSendValidation) {
                        is ZecSendExt.ZecSendValidation.Valid -> sendViewModel.onEnterReceiverAddressContinue(
                            it,
                            zecSendValidation.zecSend
                        )

                        is ZecSendExt.ZecSendValidation.Invalid -> {
                            Twig.error { "Error in onContinue after adding address ${zecSendValidation.validationErrors}" }
                            Toast.makeText(activity, "Error in validation", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                }
            )
        }

        SendUIState.REVIEW_AND_SEND -> {
            val synchronizer = walletViewModel.synchronizer.collectAsStateWithLifecycle().value
            val spendingKey = walletViewModel.spendingKey.collectAsStateWithLifecycle().value
            ReviewAndSend(
                sendAndReviewUiState = sendViewModel.sendAndReviewUiState(),
                onBack = sendViewModel::onPreviousSendUiState,
                onSendZCash = {
                    activityScope.launch {
                        val proposal = sendViewModel.onSendZCash(
                            sendViewModel.zecSend,
                            spendingKey,
                            synchronizer
                        )
                        if (proposal == null || synchronizer == null || spendingKey == null) {
                            sendViewModel.updateConfirmationState(SendConfirmationState.Failed)
                        } else {
                            val result = createTransactionsViewModel.runCreateTransactions(
                                synchronizer = synchronizer,
                                spendingKey = spendingKey,
                                proposal = proposal
                            )
                            when (result) {
                                SubmitResult.Success -> {
                                    Twig.info { "Send transaction done successfully" }
                                    sendViewModel.updateConfirmationState(SendConfirmationState.Success)
                                    // Triggering transaction history refresh to be notified about the newly created
                                    // transaction asap
                                    (synchronizer as SdkSynchronizer).run {
                                        refreshTransactions()
                                        refreshAllBalances()
                                    }
                                }

                                is SubmitResult.SimpleTrxFailure -> {
                                    Twig.warn { "Send transaction failed ${result.errorDescription}" }
                                    sendViewModel.updateConfirmationState(SendConfirmationState.Failed)
                                }

                                is SubmitResult.MultipleTrxFailure -> {
                                    Twig.warn { "Send failed with multi-transactions-submission-error handling" }
                                    sendViewModel.updateConfirmationState(SendConfirmationState.Failed)
                                }
                            }
                        }
                    }
                }
            )
        }

        SendUIState.SEND_CONFIRMATION -> {
            SendConfirmation(
                sendConfirmationState = sendViewModel.sendConfirmationState.collectAsStateWithLifecycle().value,
                onCancel = {
                    sendViewModel.clearViewModelSavedData()
                    navigateTo(BottomNavItem.Transfer.route)

                },
                onTryAgain = {
                    sendViewModel.resetSendConfirmationState()
                    sendViewModel.onPreviousSendUiState()
                },
                onDone = {
                    sendViewModel.clearViewModelSavedData()
                    navigateTo(BottomNavItem.Transfer.route)
                },
                /*onMoreDetails = {
                    sendViewModel.clearViewModelSavedData()
                    onMoreDetails(it)
                }*/
            )
        }

        null -> onBack.invoke()
    }
}
