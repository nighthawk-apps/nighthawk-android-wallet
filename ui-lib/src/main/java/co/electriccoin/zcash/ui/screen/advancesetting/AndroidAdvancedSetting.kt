package co.electriccoin.zcash.ui.screen.advancesetting

import android.app.ActivityManager
import android.content.Context.ACTIVITY_SERVICE
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.electriccoin.zcash.ui.MainActivity
import co.electriccoin.zcash.ui.screen.advancesetting.model.AvailableLogo
import co.electriccoin.zcash.ui.screen.advancesetting.view.AdvanceSetting
import co.electriccoin.zcash.ui.screen.settings.viewmodel.SettingsViewModel
import kotlinx.collections.immutable.toPersistentList

@Composable
internal fun MainActivity.AndroidAdvancedSetting(onBack: () -> Unit) {
    WrapAdvanceSetting(activity = this, onBack = onBack)
}

@Composable
internal fun WrapAdvanceSetting(activity: ComponentActivity, onBack: () -> Unit) {
    val settingsViewModel = viewModel<SettingsViewModel>()
    val isScreenOnEnabled = settingsViewModel.isKeepScreenOnWhileSyncing.collectAsStateWithLifecycle().value
    val isBanditAvailable = settingsViewModel.isBanditAvailable.collectAsStateWithLifecycle().value
    val preferredLogo = settingsViewModel.preferredLogo.collectAsStateWithLifecycle().value
    AdvanceSetting(
        isScreenOnEnabled = isScreenOnEnabled,
        isBanditAvailable = isBanditAvailable,
        preferredLogo = preferredLogo,
        allAvailableLogo = AvailableLogo.entries.toPersistentList(),
        onScreenOnEnabledChanged =
        settingsViewModel::setKeepScreenOnWhileSyncing,
        onBack = onBack,
        onNukeWallet = {
            (activity.getSystemService(ACTIVITY_SERVICE) as ActivityManager)
                .clearApplicationUserData()
        },
        onLogoPreferenceChanged = settingsViewModel::setPreferredLogo
    )
}
