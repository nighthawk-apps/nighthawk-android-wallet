@file:Suppress("ktlint:standard:filename")

package com.nighthawkapps.lib.android.ui.screen.address

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nighthawkapps.lib.android.spackle.ClipboardManagerUtil
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.screen.address.view.WalletAddresses
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.WalletViewModel

@Composable
internal fun MainActivity.WrapWalletAddresses(goBack: () -> Unit) {
    WrapWalletAddresses(this, goBack)
}

@Composable
private fun WrapWalletAddresses(
    activity: ComponentActivity,
    goBack: () -> Unit
) {
    val walletViewModel by activity.viewModels<WalletViewModel>()

    val walletAddresses = walletViewModel.addresses.collectAsStateWithLifecycle().value

    if (null == walletAddresses) {
        // Display loading indicator
    } else {
        WalletAddresses(
            walletAddresses,
            goBack,
            onCopyToClipboard = { address ->
                ClipboardManagerUtil.copyToClipboard(
                    activity.applicationContext,
                    activity.getString(R.string.wallet_address_clipboard_tag),
                    address
                )
            },
        )
    }
}
