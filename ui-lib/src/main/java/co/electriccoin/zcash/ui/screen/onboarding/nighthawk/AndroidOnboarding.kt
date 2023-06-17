package co.electriccoin.zcash.ui.screen.onboarding.nighthawk

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.MainActivity
import co.electriccoin.zcash.ui.common.PRIVACY_POLICY_LINK
import co.electriccoin.zcash.ui.common.onLaunchUrl
import co.electriccoin.zcash.ui.screen.home.viewmodel.WalletViewModel
import co.electriccoin.zcash.ui.screen.onboarding.nighthawk.view.GetStarted
import co.electriccoin.zcash.ui.screen.onboarding.nighthawk.view.RestoreWallet
import co.electriccoin.zcash.ui.screen.onboarding.viewmodel.OnboardingViewModel

@Composable
internal fun MainActivity.WrapOnBoarding() {
    WrapOnBoarding(this)
}

@Composable
internal fun WrapOnBoarding(activity: ComponentActivity) {
    val walletViewModel by activity.viewModels<WalletViewModel>()
    val onBoardingViewModel by activity.viewModels<OnboardingViewModel>()

    if (!onBoardingViewModel.isImporting.collectAsStateWithLifecycle().value) {
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
                activity.onLaunchUrl(url = PRIVACY_POLICY_LINK)
            }
        )
    } else {
        RestoreWallet(activity)
    }
}