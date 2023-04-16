package co.electriccoin.zcash.ui.screen.wallet

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.MainActivity
import co.electriccoin.zcash.ui.configuration.ConfigurationEntries
import co.electriccoin.zcash.ui.configuration.RemoteConfig
import co.electriccoin.zcash.ui.screen.home.viewmodel.WalletViewModel
import co.electriccoin.zcash.ui.screen.settings.viewmodel.SettingsViewModel
import co.electriccoin.zcash.ui.screen.wallet.view.WalletView

@Composable
internal fun MainActivity.AndroidWallet() {
    WrapWallet(activity = this)
}

@Composable
internal fun WrapWallet(activity: ComponentActivity) {
    val walletViewModel by activity.viewModels<WalletViewModel>()
    val walletSnapshot = walletViewModel.walletSnapshot.collectAsStateWithLifecycle().value

    val settingsViewModel by activity.viewModels<SettingsViewModel>()
    val isKeepScreenOnWhileSyncing = settingsViewModel.isKeepScreenOnWhileSyncing.collectAsStateWithLifecycle().value
    val isFiatConversionEnabled = ConfigurationEntries.IS_FIAT_CONVERSION_ENABLED.getValue(RemoteConfig.current)

    if (null == walletSnapshot) {
        // We can show progress bar
    } else {
        WalletView(walletSnapshot, isKeepScreenOnWhileSyncing, isFiatConversionEnabled)
    }
    activity.reportFullyDrawn()
}
