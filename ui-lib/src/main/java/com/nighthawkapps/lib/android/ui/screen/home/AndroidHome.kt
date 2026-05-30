@file:Suppress("ktlint:standard:filename")

package com.nighthawkapps.lib.android.ui.screen.home

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nighthawkapps.lib.android.spackle.EmulatorWtfUtil
import com.nighthawkapps.lib.android.spackle.FirebaseTestLabUtil
import com.nighthawkapps.lib.android.ui.BuildConfig
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.common.closeDrawerMenu
import com.nighthawkapps.lib.android.ui.configuration.ConfigurationEntries
import com.nighthawkapps.lib.android.ui.configuration.RemoteConfig
import com.nighthawkapps.lib.android.ui.screen.home.view.Home
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.WalletViewModel
import com.nighthawkapps.lib.android.ui.screen.settings.viewmodel.SettingsViewModel
import kotlinx.coroutines.CoroutineScope

@Composable
@Suppress("LongParameterList")
internal fun MainActivity.WrapHome(
    goSeedPhrase: () -> Unit,
    goSettings: () -> Unit,
    goSupport: () -> Unit,
    goAbout: () -> Unit,
    goReceive: () -> Unit,
    goSend: () -> Unit,
    goHistory: () -> Unit
) {
    WrapHome(
        this,
        goSeedPhrase = goSeedPhrase,
        goSettings = goSettings,
        goSupport = goSupport,
        goAbout = goAbout,
        goReceive = goReceive,
        goSend = goSend,
        goHistory = goHistory,
    )
}

@Composable
@Suppress("LongParameterList")
internal fun WrapHome(
    activity: ComponentActivity,
    goSeedPhrase: () -> Unit,
    goSettings: () -> Unit,
    goSupport: () -> Unit,
    goAbout: () -> Unit,
    goReceive: () -> Unit,
    goSend: () -> Unit,
    goHistory: () -> Unit,
) {
    val walletViewModel by activity.viewModels<WalletViewModel>()
    val walletSnapshot = walletViewModel.walletSnapshot.collectAsStateWithLifecycle().value

    val settingsViewModel by activity.viewModels<SettingsViewModel>()

    val isKeepScreenOnWhileSyncing = settingsViewModel.isKeepScreenOnWhileSyncing.collectAsStateWithLifecycle().value
    val isFiatConversionEnabled = ConfigurationEntries.IS_FIAT_CONVERSION_ENABLED.getValue(RemoteConfig.current)
    val isCircularProgressBarEnabled =
        ConfigurationEntries.IS_HOME_CIRCULAR_PROGRESS_BAR_ENABLED.getValue(RemoteConfig.current)

    if (null == walletSnapshot) {
        // Display loading indicator
    } else {
        val context = LocalContext.current

        // We might eventually want to check the debuggable property of the manifest instead
        // of relying on BuildConfig.
        val isDebugMenuEnabled =
            BuildConfig.DEBUG &&
                !FirebaseTestLabUtil.isFirebaseTestLab(context) &&
                !EmulatorWtfUtil.isEmulatorWtf(context)

        val drawerValues = drawerBackHandler()

        Home(
            walletSnapshot,
            isUpdateAvailable = false,
            isKeepScreenOnDuringSync = isKeepScreenOnWhileSyncing,
            isFiatConversionEnabled = isFiatConversionEnabled,
            isCircularProgressBarEnabled = isCircularProgressBarEnabled,
            isDebugMenuEnabled = isDebugMenuEnabled,
            goSeedPhrase = goSeedPhrase,
            goSettings = goSettings,
            goSupport = goSupport,
            goAbout = goAbout,
            goReceive = goReceive,
            goSend = goSend,
            goHistory = goHistory,
            resetSdk = {
                walletViewModel.resetSdk()
            },
            drawerState = drawerValues.drawerState,
            scope = drawerValues.scope
        )

        activity.reportFullyDrawn()
    }
}

/**
 * Custom Drawer menu composable with back navigation handling feature, which returns its necessary values.
 */
@Composable
internal fun drawerBackHandler(
    drawerState: DrawerState = rememberDrawerState(DrawerValue.Closed),
    scope: CoroutineScope = rememberCoroutineScope()
): DrawerValuesWrapper {
    // Override Android back navigation action to close drawer, if opened
    BackHandler(drawerState.isOpen) {
        drawerState.closeDrawerMenu(scope)
    }
    return DrawerValuesWrapper(drawerState, scope)
}

internal data class DrawerValuesWrapper(
    val drawerState: DrawerState,
    val scope: CoroutineScope
)
