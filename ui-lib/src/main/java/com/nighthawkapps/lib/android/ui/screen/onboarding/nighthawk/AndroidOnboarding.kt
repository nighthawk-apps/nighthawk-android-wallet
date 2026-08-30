package com.nighthawkapps.lib.android.ui.screen.onboarding.nighthawk

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.onLaunchUrl
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.WalletViewModel
import com.nighthawkapps.lib.android.ui.screen.onboarding.nighthawk.view.GetStarted
import com.nighthawkapps.lib.android.ui.screen.onboarding.nighthawk.view.OnboardingCarousel
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

    val prefs = activity.getSharedPreferences("nighthawk_onboarding", Context.MODE_PRIVATE)
    var showCarousel by remember { mutableStateOf(!prefs.getBoolean("has_completed_onboarding", false)) }

    if (showCarousel) {
        LaunchedEffect(Unit) {
            prefs.edit().putBoolean("has_completed_onboarding", true).apply()
        }
        OnboardingCarousel(onComplete = {
            showCarousel = false
        })
    } else if (!onBoardingViewModel.isImporting.collectAsStateWithLifecycle().value) {
        val createWalletError = walletViewModel.createWalletError.collectAsStateWithLifecycle().value
        val onCreateWallet = {
            walletViewModel.persistNewWallet()
        }
        val onRestore = {
            onBoardingViewModel.setIsImporting(true)
        }

        GetStarted(
            onCreateWallet = onCreateWallet,
            onRestore = onRestore,
            onReference = {
                activity.onLaunchUrl(url = activity.getString(R.string.ns_privacy_policy_link))
            },
            createWalletError = createWalletError,
        )
    } else {
        RestoreWallet(activity)
    }
}
