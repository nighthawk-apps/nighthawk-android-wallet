package co.electriccoin.zcash.ui.screen.topup

import androidx.compose.runtime.Composable
import co.electriccoin.zcash.ui.MainActivity
import co.electriccoin.zcash.ui.common.onLaunchUrl
import co.electriccoin.zcash.ui.screen.topup.view.TopUp

@Composable
internal fun MainActivity.AndroidTopUp(onBack: () -> Unit) {
    WrapTopUp(
        onBack = onBack,
        onLaunchUrl = {
            onLaunchUrl(it)
        }
    )
}

@Composable
internal fun WrapTopUp(onBack: () -> Unit, onLaunchUrl: (String) -> Unit) {
    TopUp(onBack = onBack, onLaunchUrl = { onLaunchUrl(it) })
}
