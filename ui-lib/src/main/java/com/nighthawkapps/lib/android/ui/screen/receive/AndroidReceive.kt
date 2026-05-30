@file:Suppress("ktlint:standard:filename")

package com.nighthawkapps.lib.android.ui.screen.receive

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiWalletAddresses
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.WalletViewModel
import com.nighthawkapps.lib.android.ui.screen.receive.view.Receive as ReceiveScreen

@Composable
@Suppress("LongParameterList")
internal fun MainActivity.WrapReceive(
    onBack: () -> Unit,
    onAddressDetails: () -> Unit,
) {
    WrapReceive(
        this,
        onBack = onBack,
        onAddressDetails = onAddressDetails,
    )
}

@Composable
@Suppress("LongParameterList")
internal fun WrapReceive(
    activity: ComponentActivity,
    onBack: () -> Unit,
    onAddressDetails: () -> Unit,
) {
    val viewModel by activity.viewModels<WalletViewModel>()
    val walletAddresses = viewModel.addresses.collectAsStateWithLifecycle().value

    WrapReceive(
        walletAddresses,
        onBack = onBack,
        onAddressDetails = onAddressDetails,
    )
}

@Composable
@Suppress("LongParameterList")
internal fun WrapReceive(
    walletAddresses: DarkfiWalletAddresses?,
    onBack: () -> Unit,
    onAddressDetails: () -> Unit,
) {
    if (null == walletAddresses) {
        // Display loading indicator
    } else {
        ReceiveScreen(
            depositUriPrimary = walletAddresses.privateAddresses.first(),
            onBack = onBack,
            onAddressDetails = onAddressDetails,
        )
    }
}
