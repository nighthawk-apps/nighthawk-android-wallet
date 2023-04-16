package co.electriccoin.zcash.ui.screen.transfer

import androidx.compose.runtime.Composable
import androidx.core.app.ComponentActivity
import co.electriccoin.zcash.ui.MainActivity

@Composable
internal fun MainActivity.AndroidTransfer() {
    WrapTransfer(activity = this)
}

@Composable
internal fun WrapTransfer(activity: ComponentActivity) {
    println("Just for initial run $activity")
}