package co.electriccoin.zcash.ui.screen.transfer

import androidx.compose.runtime.Composable
import androidx.core.app.ComponentActivity
import cash.z.ecc.android.sdk.internal.Twig
import co.electriccoin.zcash.ui.MainActivity

@Composable
internal fun MainActivity.AndroidTransfer() {
    WrapTransfer(activity = this)
}

@Composable
internal fun WrapTransfer(activity: ComponentActivity) {
    Twig.info { "Just for initial run $activity" }
    TransferMainView()
}