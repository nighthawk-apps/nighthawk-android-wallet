package com.nighthawkapps.lib.android.ui.screen.onboarding.nighthawk

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.onLaunchUrl
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.WalletViewModel
import com.nighthawkapps.lib.android.ui.screen.onboarding.nighthawk.view.GetStarted
import com.nighthawkapps.lib.android.ui.screen.onboarding.nighthawk.view.RestoreWallet
import com.nighthawkapps.lib.android.ui.screen.onboarding.viewmodel.OnboardingViewModel

@Composable
internal fun MainActivity.WrapOnBoarding() {
    WrapOnBoarding(this)
}

@Composable
internal fun WrapOnBoarding(activity: ComponentActivity) {
    val walletViewModel by activity.viewModels<WalletViewModel>()
    val onBoardingViewModel by activity.viewModels<OnboardingViewModel>()

    if (!onBoardingViewModel.isImporting.collectAsStateWithLifecycle().value) {
        val createWalletError = walletViewModel.createWalletError.collectAsStateWithLifecycle().value
        GetStarted(
            onCreateWallet = { walletViewModel.persistNewWallet() },
            onRestore = { onBoardingViewModel.setIsImporting(true) },
            onReference = {
                activity.onLaunchUrl(url = activity.getString(R.string.ns_privacy_policy_link))
            },
            createWalletError = createWalletError,
        )
    } else {
        RestoreWallet(activity)
    }
}
