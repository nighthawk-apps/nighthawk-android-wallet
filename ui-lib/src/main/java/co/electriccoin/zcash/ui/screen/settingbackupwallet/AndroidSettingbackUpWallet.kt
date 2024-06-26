package co.electriccoin.zcash.ui.screen.settingbackupwallet

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.MainActivity
import co.electriccoin.zcash.ui.screen.home.viewmodel.SecretState
import co.electriccoin.zcash.ui.screen.home.viewmodel.WalletViewModel
import co.electriccoin.zcash.ui.screen.onboarding.nighthawk.view.SeedBackup

@Composable
internal fun MainActivity.AndroidSettingBackUpWallet(onBack: () -> Unit) {
    WrapAndroidSettingBackUpWallet(activity = this, onBack = onBack)
}

@Composable
internal fun WrapAndroidSettingBackUpWallet(activity: ComponentActivity, onBack: () -> Unit) {
    val walletViewModel by activity.viewModels<WalletViewModel>()
    val secretState = walletViewModel.secretState.collectAsStateWithLifecycle().value

    // SecretSate.NeedBackUp and SecretState.Ready only contains the persistWallet
    if (secretState is SecretState.Ready) {
        SeedBackup(persistableWallet = secretState.persistableWallet, navigationFromSettings = true, onBack = onBack)
    }
}