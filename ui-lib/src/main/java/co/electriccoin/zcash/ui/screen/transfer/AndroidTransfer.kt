package co.electriccoin.zcash.ui.screen.transfer

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import cash.z.ecc.android.sdk.internal.Twig
import co.electriccoin.zcash.ui.MainActivity
import co.electriccoin.zcash.ui.screen.transfer.view.TransferMainView

@Composable
internal fun MainActivity.AndroidTransfer(onSendMoney: () -> Unit, onReceiveMoney: () -> Unit, onTopUp: () -> Unit, onPayWithFlexa: () -> Unit) {
    WrapTransfer(activity = this, onSendMoney = onSendMoney, onReceiveMoney = onReceiveMoney, onTopUp = onTopUp, onPayWithFlexa = onPayWithFlexa)
}

@Composable
internal fun WrapTransfer(activity: ComponentActivity, onSendMoney: () -> Unit, onReceiveMoney: () -> Unit, onTopUp: () -> Unit, onPayWithFlexa: () -> Unit) {
    Twig.debug { "Just for initial run $activity" }
    TransferMainView(onSendMoney = onSendMoney, onReceiveMoney = onReceiveMoney, onTopUp = onTopUp, onPayWithFlexa = onPayWithFlexa)
}