package com.nighthawkapps.lib.android.ui.screen.advancesetting

import android.app.ActivityManager
import android.content.Context.ACTIVITY_SERVICE
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.screen.advancesetting.model.AvailableLogo
import com.nighthawkapps.lib.android.ui.screen.advancesetting.view.AdvanceSetting
import com.nighthawkapps.lib.android.ui.screen.settings.viewmodel.SettingsViewModel
import kotlinx.collections.immutable.toPersistentList

@Composable
internal fun MainActivity.AndroidAdvancedSetting(onBack: () -> Unit) {
    WrapAdvanceSetting(activity = this, onBack = onBack)
}

@Composable
internal fun WrapAdvanceSetting(
    activity: ComponentActivity,
    onBack: () -> Unit
) {
    val settingsViewModel = viewModel<SettingsViewModel>()
    val isScreenOnEnabled = settingsViewModel.isKeepScreenOnWhileSyncing.collectAsStateWithLifecycle().value
    val isStrictOmrOnly = settingsViewModel.isStrictOmrOnly.collectAsStateWithLifecycle().value
    val isBanditAvailable = settingsViewModel.isBanditAvailable.collectAsStateWithLifecycle().value
    val preferredLogo = settingsViewModel.preferredLogo.collectAsStateWithLifecycle().value
    val themeVariant = settingsViewModel.appThemeVariant.collectAsStateWithLifecycle().value
    AdvanceSetting(
        isScreenOnEnabled = isScreenOnEnabled,
        isStrictOmrOnly = isStrictOmrOnly,
        isBanditAvailable = isBanditAvailable,
        preferredLogo = preferredLogo,
        themeVariant = themeVariant,
        allAvailableLogo = AvailableLogo.entries.toPersistentList(),
        onScreenOnEnabledChanged =
            settingsViewModel::setKeepScreenOnWhileSyncing,
        onStrictOmrOnlyChanged = settingsViewModel::setStrictOmrOnly,
        onBack = onBack,
        onNukeWallet = {
            (activity.getSystemService(ACTIVITY_SERVICE) as ActivityManager)
                .clearApplicationUserData()
        },
        onLogoPreferenceChanged = settingsViewModel::setPreferredLogo,
        onThemeVariantChanged = settingsViewModel::setAppThemeVariant
    )
}
