@file:Suppress("UnusedParameter")

package com.nighthawkapps.lib.android.ui.screen.send.nighthawk

import androidx.compose.runtime.Composable
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.screen.send.WrapSend
import com.nighthawkapps.lib.android.ui.screen.send.model.SendArgumentsWrapper

@Composable
internal fun MainActivity.AndroidSend(
    onBack: () -> Unit,
    onTopUpWallet: () -> Unit,
    navigateTo: (String) -> Unit,
    onScan: () -> Unit,
    sendArgumentsWrapper: SendArgumentsWrapper? = null,
) {
    WrapSend(
        sendArgumentsWrapper = sendArgumentsWrapper,
        goToQrScanner = onScan,
        goBack = onBack,
    )
}
