package com.nighthawkapps.lib.android.ui.screen.topup

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.WalletViewModel
import com.nighthawkapps.lib.android.ui.screen.topup.view.TopUp

@Composable
internal fun MainActivity.AndroidTopUp(onBack: () -> Unit) {
    WrapTopUp(activity = this, onBack = onBack)
}

@Composable
internal fun WrapTopUp(
    activity: ComponentActivity,
    onBack: () -> Unit
) {
    val walletViewModel by activity.viewModels<WalletViewModel>()
    val walletAddress = walletViewModel.addresses.collectAsStateWithLifecycle().value
    TopUp(walletAddress = walletAddress, onBack = onBack)
}
