package com.nighthawkapps.lib.android.ui.screen.receive.nighthawk

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nighthawkapps.lib.android.spackle.ClipboardManagerUtil
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.WalletViewModel
import com.nighthawkapps.lib.android.ui.screen.receive.nighthawk.view.ReceiveView

@Composable
internal fun MainActivity.AndroidReceive(
    onBack: () -> Unit,
    onShowQrCode: () -> Unit,
    onTopUpWallet: () -> Unit
) {
    WrapReceive(this, onBack = onBack, onShowQrCode = onShowQrCode, onTopUpWallet = onTopUpWallet)
}

@Composable
internal fun WrapReceive(
    activity: ComponentActivity,
    onBack: () -> Unit,
    onShowQrCode: () -> Unit,
    onTopUpWallet: () -> Unit
) {
    val walletViewModel by activity.viewModels<WalletViewModel>()
    val walletAddresses = walletViewModel.addresses.collectAsStateWithLifecycle().value
    ReceiveView(
        onBack = onBack,
        onShowQrCode = onShowQrCode,
        onCopyPrivateAddress = {
            ClipboardManagerUtil.copyToClipboard(
                activity.applicationContext,
                activity.getString(R.string.ns_private_address),
                walletAddresses?.privateAddresses?.firstOrNull().orEmpty()
            )
        },
        onTopUpWallet = onTopUpWallet
    )
}
