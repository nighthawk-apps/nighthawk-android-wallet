package co.electriccoin.zcash.ui.screen.settings.nighthawk

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import co.electriccoin.zcash.ui.MainActivity
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.VersionInfo
import co.electriccoin.zcash.ui.common.showMessage
import co.electriccoin.zcash.ui.screen.home.viewmodel.WalletViewModel
import co.electriccoin.zcash.ui.screen.settings.nighthawk.model.ReScanType
import co.electriccoin.zcash.ui.screen.settings.nighthawk.view.SettingsView

@Composable
internal fun MainActivity.AndroidSettings(
    onSyncNotifications: () -> Unit,
    onFiatCurrency: () -> Unit,
    onSecurity: () -> Unit,
    onBackupWallet: () -> Unit,
    onAdvancedSetting: () -> Unit,
    onChangeServer: () -> Unit,
    onExternalServices: () -> Unit,
    onAbout: () -> Unit
) {
    WrapSettings(
        activity = this,
        onSyncNotifications = onSyncNotifications,
        onFiatCurrency = onFiatCurrency,
        onSecurity = onSecurity,
        onBackupWallet = onBackupWallet,
        onAdvancedSetting = onAdvancedSetting,
        onChangeServer = onChangeServer,
        onExternalServices = onExternalServices,
        onAbout = onAbout
    )
}

@Composable
internal fun WrapSettings(
    activity: ComponentActivity,
    onSyncNotifications: () -> Unit,
    onFiatCurrency: () -> Unit,
    onSecurity: () -> Unit,
    onBackupWallet: () -> Unit,
    onAdvancedSetting: () -> Unit,
    onChangeServer: () -> Unit,
    onExternalServices: () -> Unit,
    onAbout: () -> Unit
) {
    val walletViewModel by activity.viewModels<WalletViewModel>()

    val onReScan: (ReScanType) -> Unit = {
        when (it) {
            ReScanType.FULL_SCAN -> {
                walletViewModel.rescanBlockchain()
                activity.showMessage(activity.getString(R.string.dialog_rescan_initiated_title))
            }
            ReScanType.WIPE -> {
                walletViewModel.resetSdk()
                activity.showMessage(activity.getString(R.string.rescan_wallet_wipe_success))
            }
        }
    }

    SettingsView(
        versionInfo = VersionInfo.new(activity),
        onSyncNotifications = onSyncNotifications,
        onFiatCurrency = onFiatCurrency,
        onSecurity = onSecurity,
        onBackupWallet = onBackupWallet,
        onRescan = onReScan,
        onAdvancedSetting = onAdvancedSetting,
        onChangeServer = onChangeServer,
        onExternalServices = onExternalServices,
        onAbout = onAbout
    )
}