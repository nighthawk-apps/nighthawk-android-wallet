package co.electriccoin.zcash.ui.screen.send.nighthawk

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.MainActivity
import co.electriccoin.zcash.ui.screen.home.viewmodel.HomeViewModel
import co.electriccoin.zcash.ui.screen.send.nighthawk.model.SendAndReviewUiState
import co.electriccoin.zcash.ui.screen.send.nighthawk.model.SendUIState
import co.electriccoin.zcash.ui.screen.send.nighthawk.view.EnterMessage
import co.electriccoin.zcash.ui.screen.send.nighthawk.view.EnterReceiverAddress
import co.electriccoin.zcash.ui.screen.send.nighthawk.view.EnterZec
import co.electriccoin.zcash.ui.screen.send.nighthawk.view.ReviewAndSend
import co.electriccoin.zcash.ui.screen.send.nighthawk.viewmodel.SendViewModel

@Composable
internal fun MainActivity.AndroidSend(onBack: () -> Unit) {
    WrapAndroidSend(activity = this, onBack = onBack, onViewOnExplorer = {})
}

@Composable
internal fun WrapAndroidSend(activity: ComponentActivity, onBack: () -> Unit, onViewOnExplorer: () -> Unit) {
    val homeViewModel by activity.viewModels<HomeViewModel>()
    val sendViewModel by activity.viewModels<SendViewModel>()
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
                onBack = sendViewModel::onPreviousSendUiState,
                onContinue = sendViewModel::onEnterMessageContinue
            )
        }
        SendUIState.ENTER_ADDRESS -> {
            EnterReceiverAddress(
                onBack = sendViewModel::onPreviousSendUiState,
                onContinue = sendViewModel::onEnterReceiverAddressContinue
            )
        }
        SendUIState.REVIEW_AND_SEND -> {
            ReviewAndSend(
                sendAndReviewUiState = SendAndReviewUiState()
                    .copy(
                        amountToSend = "21",
                        memo = "Send the cool stuff with full privacy",
                        receiverAddress = "zs1j29m7zdhhyy2eqrz89l4zhk0angqjh368gqkj2vgdyqmeuultteny36n3qsm47zn8du5sw3ts7f",
                        subTotal = "21",
                        networkFees = "0.00001",
                        totalAmount = "21.0001"
                    ),
                onBack = sendViewModel::onPreviousSendUiState,
                onViewOnExplorer = onViewOnExplorer,
                onSendZCash = sendViewModel::onSendZCash
            )
        }
        SendUIState.SEND_CONFIRMATION -> {}
        null -> onBack.invoke()
    }
}
