package com.nighthawkapps.lib.android.ui.screen.receiveqrcodes

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nighthawkapps.lib.android.spackle.Twig
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.WalletViewModel
import com.nighthawkapps.lib.android.ui.screen.receiveqrcodes.view.ReceiveQrCodes

@Composable
internal fun MainActivity.AndroidReceiveQrCodes(
    onBack: () -> Unit,
    onSeeMoreTopUpOption: () -> Unit
) {
    WrapReceiveQrCodes(activity = this, onBack = onBack, onSeeMoreTopUpOption = onSeeMoreTopUpOption)
}

@Composable
fun WrapReceiveQrCodes(
    activity: ComponentActivity,
    onBack: () -> Unit,
    onSeeMoreTopUpOption: () -> Unit
) {
    val walletViewModel by activity.viewModels<WalletViewModel>()
    val walletAddresses = walletViewModel.addresses.collectAsStateWithLifecycle().value
    if (walletAddresses == null) {
        Twig.debug { "WalletAddress is null" } // We can show loading dialog or error
    } else {
        ReceiveQrCodes(
            walletAddresses = walletAddresses,
            onBack = onBack,
            onSeeMoreTopUpOption = onSeeMoreTopUpOption,
            onCreateNewAddress = {
                walletViewModel.generateNewAddress()
            }
        )
    }
}
