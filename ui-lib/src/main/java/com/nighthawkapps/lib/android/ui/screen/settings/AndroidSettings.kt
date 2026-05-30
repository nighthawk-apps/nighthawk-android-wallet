@file:Suppress("ktlint:standard:filename")

package com.nighthawkapps.lib.android.ui.screen.settings

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.configuration.ConfigurationEntries
import com.nighthawkapps.lib.android.ui.configuration.RemoteConfig
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.WalletViewModel
import com.nighthawkapps.lib.android.ui.screen.settings.view.Settings
import com.nighthawkapps.lib.android.ui.screen.settings.viewmodel.SettingsViewModel

@Composable
internal fun MainActivity.WrapSettings(goBack: () -> Unit) {
    WrapSettings(
        activity = this,
        goBack = goBack,
    )
}

@Composable
private fun WrapSettings(
    activity: ComponentActivity,
    goBack: () -> Unit,
) {
    val walletViewModel by activity.viewModels<WalletViewModel>()
    val settingsViewModel by activity.viewModels<SettingsViewModel>()

    val synchronizer = walletViewModel.synchronizer.collectAsStateWithLifecycle().value
    val isBackgroundSyncEnabled = settingsViewModel.isBackgroundSync.collectAsStateWithLifecycle().value
    val isKeepScreenOnWhileSyncing = settingsViewModel.isKeepScreenOnWhileSyncing.collectAsStateWithLifecycle().value
    val isAnalyticsEnabled = settingsViewModel.isAnalyticsEnabled.collectAsStateWithLifecycle().value

    @Suppress("ComplexCondition")
    if (null == synchronizer ||
        null == isAnalyticsEnabled ||
        null == isBackgroundSyncEnabled ||
        null == isKeepScreenOnWhileSyncing
    ) {
        // Display loading indicator
    } else {
        Settings(
            isBackgroundSyncEnabled = isBackgroundSyncEnabled,
            isKeepScreenOnDuringSyncEnabled = isKeepScreenOnWhileSyncing,
            isAnalyticsEnabled = isAnalyticsEnabled,
            isRescanEnabled = ConfigurationEntries.IS_RESCAN_ENABLED.getValue(RemoteConfig.current),
            onBack = goBack,
            onRescanWallet = {
                walletViewModel.rescanBlockchain()
            },
            onBackgroundSyncSettingsChanged = {
                settingsViewModel.setBackgroundSyncEnabled(it)
            },
            onIsKeepScreenOnDuringSyncSettingsChanged = {
                settingsViewModel.setKeepScreenOnWhileSyncing(it)
            },
            onAnalyticsSettingsChanged = {
                settingsViewModel.setAnalyticsEnabled(it)
            }
        )
    }
}
