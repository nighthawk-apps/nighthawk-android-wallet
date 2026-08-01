@file:Suppress("ktlint:standard:filename")

package com.nighthawkapps.lib.android.ui.screen.seed

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.common.setSensitivePlainText
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.SecretState
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.WalletViewModel
import com.nighthawkapps.lib.android.ui.screen.seed.view.Seed
import kotlinx.coroutines.launch

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
        val clipboard = LocalClipboard.current
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        Seed(
            persistableWallet = persistableWallet,
            onBack = goBack,
            onCopyToClipboard = {
                scope.launch {
                    clipboard.setSensitivePlainText(
                        context = context,
                        text = persistableWallet.seedPhrase.joinToString(),
                        label = "Seed",
                    )
                }
            },
        )
    }
}
