@file:Suppress("ktlint:standard:filename")

package com.nighthawkapps.lib.android.ui.screen.seed

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nighthawkapps.lib.android.spackle.ClipboardManagerUtil
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.SecretState
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.WalletViewModel
import com.nighthawkapps.lib.android.ui.screen.seed.view.Seed

@Composable
internal fun MainActivity.WrapSeed(goBack: () -> Unit) {
    WrapSeed(this, goBack)
}

@Composable
private fun WrapSeed(
    activity: ComponentActivity,
    goBack: () -> Unit
) {
    val walletViewModel by activity.viewModels<WalletViewModel>()

    val persistableWallet =
        run {
            val secretState = walletViewModel.secretState.collectAsStateWithLifecycle().value
            if (secretState is SecretState.Ready) {
                secretState.persistableWallet
            } else {
                null
            }
        }
    val synchronizer = walletViewModel.synchronizer.collectAsStateWithLifecycle().value

    if (null == synchronizer || null == persistableWallet) {
        // Display loading indicator
    } else {
        Seed(
            persistableWallet = persistableWallet,
            onBack = goBack,
            onCopyToClipboard = {
                ClipboardManagerUtil.copyToClipboard(
                    activity.applicationContext,
                    activity.getString(R.string.new_wallet_clipboard_tag),
                    persistableWallet.seedPhrase.joinToString()
                )
            },
        )
    }
}
