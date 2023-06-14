package co.electriccoin.zcash.ui.screen.settings.nighthawk

import androidx.compose.runtime.Composable
import androidx.core.app.ComponentActivity
import co.electriccoin.zcash.spackle.getPackageInfoCompat
import co.electriccoin.zcash.ui.MainActivity
import co.electriccoin.zcash.ui.screen.about.model.VersionInfo
import co.electriccoin.zcash.ui.screen.settings.nighthawk.view.SettingsView

@Composable
internal fun MainActivity.AndroidSettings(
    onSyncNotifications: () -> Unit,
    onFiatCurrency: () -> Unit,
    onSecurity: () -> Unit,
    onBackupWallet: () -> Unit,
    onRescan: () -> Unit,
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
        onRescan = onRescan,
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
    onRescan: () -> Unit,
    onChangeServer: () -> Unit,
    onExternalServices: () -> Unit,
    onAbout: () -> Unit
) {
    val packageInfo = activity.packageManager.getPackageInfoCompat(activity.packageName, 0L)
    SettingsView(
        versionInfo = VersionInfo.new(packageInfo),
        onSyncNotifications = onSyncNotifications,
        onFiatCurrency = onFiatCurrency,
        onSecurity = onSecurity,
        onBackupWallet = onBackupWallet,
        onRescan = onRescan,
        onChangeServer = onChangeServer,
        onExternalServices = onExternalServices,
        onAbout = onAbout
    )
}