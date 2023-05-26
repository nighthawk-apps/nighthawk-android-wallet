package co.electriccoin.zcash.ui.screen.send.nighthawk

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cash.z.ecc.android.sdk.ext.ZcashSdk
import cash.z.ecc.android.sdk.model.MonetarySeparators
import cash.z.ecc.android.sdk.model.ZecSendExt
import cash.z.ecc.android.sdk.model.toZecString
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.MainActivity
import co.electriccoin.zcash.ui.screen.home.viewmodel.HomeViewModel
import co.electriccoin.zcash.ui.screen.home.viewmodel.WalletViewModel
import co.electriccoin.zcash.ui.screen.send.ext.ABBREVIATION_INDEX
import co.electriccoin.zcash.ui.screen.send.nighthawk.model.SendAndReviewUiState
import co.electriccoin.zcash.ui.screen.send.nighthawk.model.SendConfirmationState
import co.electriccoin.zcash.ui.screen.send.nighthawk.model.SendUIState
import co.electriccoin.zcash.ui.screen.send.nighthawk.view.EnterMessage
import co.electriccoin.zcash.ui.screen.send.nighthawk.view.EnterReceiverAddress
import co.electriccoin.zcash.ui.screen.send.nighthawk.view.EnterZec
import co.electriccoin.zcash.ui.screen.send.nighthawk.view.ReviewAndSend
import co.electriccoin.zcash.ui.screen.send.nighthawk.view.SendConfirmation
import co.electriccoin.zcash.ui.screen.send.nighthawk.viewmodel.SendViewModel
import kotlinx.coroutines.launch

@Composable
internal fun MainActivity.AndroidSend(onBack: () -> Unit) {
    WrapAndroidSend(activity = this, onBack = onBack, onViewOnExplorer = {})
}

@Composable
internal fun WrapAndroidSend(activity: ComponentActivity, onBack: () -> Unit, onViewOnExplorer: () -> Unit) {
    val homeViewModel by activity.viewModels<HomeViewModel>()
    val sendViewModel by activity.viewModels<SendViewModel>()
    val walletViewModel by activity.viewModels<WalletViewModel>()

    val sendUIState = sendViewModel.currentSendUIState.collectAsStateWithLifecycle()
    BackHandler(enabled = sendUIState.value != SendUIState.ENTER_ZEC) {
        Twig.info { "WrapAndroidSend BackHandler: sendUIState $sendUIState" }
    }
    DisposableEffect(key1 = Unit) {
        homeViewModel.onBottomNavBarVisibilityChanged(show = false)
        onDispose {
            Twig.info { "WrapAndroidSend: onDispose $sendUIState" }
            homeViewModel.onBottomNavBarVisibilityChanged(show = true)
        }
    }

    when (sendUIState.value) {
        SendUIState.ENTER_ZEC -> {
            walletViewModel.walletSnapshot.collectAsStateWithLifecycle().value?.let(sendViewModel::updateEnterZecUiStateWithWalletSnapshot)
            val enterZecUIState = sendViewModel.enterZecUIState.collectAsStateWithLifecycle()
            EnterZec(
                enterZecUIState = enterZecUIState.value,
                onBack = onBack,
                onScanPaymentCode = {},
                onContinue = sendViewModel::onNextSendUiState,
                onTopUpWallet = {},
                onNotEnoughZCash = {},
                onKeyPressed = sendViewModel::onKeyPressed
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
            EnterReceiverAddress(
                isContinueBtnEnabled = isContinueEnabled,
                onBack = sendViewModel::onPreviousSendUiState,
                onValueChanged = { address ->
                    if (address.length <= ABBREVIATION_INDEX) {
                        isContinueEnabled = false
                        return@EnterReceiverAddress
                    }
                    scope.launch {
                        synchronizer?.let {
                            isContinueEnabled = it.validateAddress(address).isNotValid.not()
                        }
                    }
                },
                onContinue = {
                    val zecSendValidation = ZecSendExt.new(
                        activity,
                        it,
                        sendViewModel.enterZecUIState.value.enteredAmount,
                        sendViewModel.userEnteredMemo,
                        MonetarySeparators.current().copy(decimal = '.')
                    )

                    when (zecSendValidation) {
                        is ZecSendExt.ZecSendValidation.Valid -> sendViewModel.onEnterReceiverAddressContinue(it, zecSendValidation.zecSend)
                        is ZecSendExt.ZecSendValidation.Invalid -> {
                            Twig.error { "Error in onContinue after adding address ${zecSendValidation.validationErrors}" }
                            Toast.makeText(activity, "Error in validation", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }

        SendUIState.REVIEW_AND_SEND -> {
            /* val synchronizer = walletViewModel.synchronizer.collectAsStateWithLifecycle().value
             val spendingKey = walletViewModel.spendingKey.collectAsStateWithLifecycle().value*/
            ReviewAndSend(
                sendAndReviewUiState = SendAndReviewUiState()
                    .copy(
                        amountToSend = sendViewModel.zecSend?.amount?.toZecString() ?: "",
                        memo = sendViewModel.zecSend?.memo?.value ?: "",
                        receiverAddress = sendViewModel.zecSend?.destination?.address ?: "",
                        subTotal = sendViewModel.zecSend?.amount?.toZecString() ?: "",
                        networkFees = "${ZcashSdk.MINERS_FEE.value}",
                        totalAmount = "${sendViewModel.zecSend?.amount?.plus(ZcashSdk.MINERS_FEE)?.toZecString()}"
                    ),
                onBack = sendViewModel::onPreviousSendUiState,
                onViewOnExplorer = onViewOnExplorer,
                onSendZCash = sendViewModel::onSendZCash
            )
        }

        SendUIState.SEND_CONFIRMATION -> {
            SendConfirmation(
                sendConfirmationState = SendConfirmationState.Success,
                onCancel = {},
                onTryAgain = sendViewModel::onPreviousSendUiState,
                onDone = {},
                onMoreDetails = {}
            )
        }

        null -> onBack.invoke()
    }
}
