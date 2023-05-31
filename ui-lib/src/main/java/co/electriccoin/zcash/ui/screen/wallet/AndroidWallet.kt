package co.electriccoin.zcash.ui.screen.wallet

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.MainActivity
import co.electriccoin.zcash.ui.configuration.ConfigurationEntries
import co.electriccoin.zcash.ui.configuration.RemoteConfig
import co.electriccoin.zcash.ui.screen.home.viewmodel.HomeViewModel
import co.electriccoin.zcash.ui.screen.home.viewmodel.WalletViewModel
import co.electriccoin.zcash.ui.screen.settings.viewmodel.SettingsViewModel
import co.electriccoin.zcash.ui.screen.wallet.view.WalletView
import co.electriccoin.zcash.ui.screen.wallet.view.isSyncing

@Composable
internal fun MainActivity.AndroidWallet() {
    WrapWallet(activity = this)
}

@Composable
internal fun WrapWallet(activity: ComponentActivity) {
    val homeViewModel by activity.viewModels<HomeViewModel>()
    val walletViewModel by activity.viewModels<WalletViewModel>()
    val walletSnapshot = walletViewModel.walletSnapshot.collectAsStateWithLifecycle().value

    val settingsViewModel by activity.viewModels<SettingsViewModel>()
    val isKeepScreenOnWhileSyncing = settingsViewModel.isKeepScreenOnWhileSyncing.collectAsStateWithLifecycle().value
    val isFiatConversionEnabled = ConfigurationEntries.IS_FIAT_CONVERSION_ENABLED.getValue(RemoteConfig.current)

    if (null == walletSnapshot) {
        // We can show progress bar
    } else {
        val isSyncing = isSyncing(walletSnapshot.status)
        LaunchedEffect(key1 = isSyncing) {
            homeViewModel.onTransferTabStateChanged(enable = isSyncing.not())
        }
        WalletView(
            walletSnapshot = walletSnapshot,
            isKeepScreenOnWhileSyncing = isKeepScreenOnWhileSyncing,
            isFiatConversionEnabled = isFiatConversionEnabled,
            onShieldNow = {
                Twig.info { "ShieldNow clicked" }
            }
        )
    }
    activity.reportFullyDrawn()
}
