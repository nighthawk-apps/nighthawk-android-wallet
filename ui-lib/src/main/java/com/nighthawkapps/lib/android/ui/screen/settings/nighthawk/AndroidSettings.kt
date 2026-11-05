package com.nighthawkapps.lib.android.ui.screen.settings.nighthawk

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.VersionInfo
import com.nighthawkapps.lib.android.ui.common.showMessage
import com.nighthawkapps.lib.android.ui.screen.about.nighthawk.AndroidAboutView
import com.nighthawkapps.lib.android.ui.screen.advancesetting.AndroidAdvancedSetting
import com.nighthawkapps.lib.android.ui.screen.changeserver.AndroidChangeServer
import com.nighthawkapps.lib.android.ui.screen.chat.AndroidChatSettings
import com.nighthawkapps.lib.android.ui.screen.dao.AndroidDaoHub
import com.nighthawkapps.lib.android.ui.screen.fiatcurrency.AndroidFiatCurrency
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.WalletViewModel
import com.nighthawkapps.lib.android.ui.screen.navigation.NighthawkExpandedWidth
import com.nighthawkapps.lib.android.ui.screen.navigation.NavigationTargets
import com.nighthawkapps.lib.android.ui.screen.security.AndroidSecurity
import com.nighthawkapps.lib.android.ui.screen.settingbackupwallet.AndroidSettingBackUpWallet
import com.nighthawkapps.lib.android.ui.screen.settings.nighthawk.model.NighthawkSettingsNavigationCallbacks
import com.nighthawkapps.lib.android.ui.screen.settings.nighthawk.model.ReScanType
import com.nighthawkapps.lib.android.ui.screen.settings.nighthawk.view.SettingsCompanionHub
import com.nighthawkapps.lib.android.ui.screen.settings.nighthawk.view.SettingsView
import com.nighthawkapps.lib.android.ui.screen.syncnotification.AndroidSyncNotification
import com.nighthawkapps.lib.android.ui.screen.topup.AndroidTopUp

@Composable
internal fun MainActivity.AndroidSettings(
    navigation: NighthawkSettingsNavigationCallbacks,
    onOpenDao: (String) -> Unit = {},
    onSetPin: () -> Unit = {},
    onDisablePin: () -> Unit = {},
) {
    WrapSettings(
        activity = this,
        navigation = navigation,
        onOpenDao = onOpenDao,
        onSetPin = onSetPin,
        onDisablePin = onDisablePin,
    )
}

@Composable
internal fun WrapSettings(
    activity: ComponentActivity,
    navigation: NighthawkSettingsNavigationCallbacks,
    onOpenDao: (String) -> Unit = {},
    onSetPin: () -> Unit = {},
    onDisablePin: () -> Unit = {},
) {
    val walletViewModel by activity.viewModels<WalletViewModel>()
    val versionInfo = VersionInfo.new(activity)

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

    val main = activity as? MainActivity
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val expanded = maxWidth >= NighthawkExpandedWidth && main != null
        var selectedRoute by rememberSaveable { mutableStateOf<String?>(null) }

        fun openOrNavigate(
            route: String,
            navigate: () -> Unit,
        ) {
            if (expanded) {
                selectedRoute = route
            } else {
                navigate()
            }
        }

        val foldNavigation =
            NighthawkSettingsNavigationCallbacks(
                onChatSettings = {
                    openOrNavigate(NavigationTargets.CHAT_SETTINGS, navigation.onChatSettings)
                },
                onMeshSettings = {
                    openOrNavigate(NavigationTargets.MESH_SETTINGS, navigation.onMeshSettings)
                },
                onTorNetworkSettings = {
                    openOrNavigate(NavigationTargets.TOR_NETWORK_SETTINGS, navigation.onTorNetworkSettings)
                },
                onSyncNotifications = {
                    openOrNavigate(NavigationTargets.SYNC_NOTIFICATION, navigation.onSyncNotifications)
                },
                onFiatCurrency = {
                    openOrNavigate(NavigationTargets.FIAT_CURRENCY, navigation.onFiatCurrency)
                },
                onSecurity = {
                    openOrNavigate(NavigationTargets.SECURITY, navigation.onSecurity)
                },
                onBackupWallet = {
                    openOrNavigate(NavigationTargets.SETTING_BACK_UP_WALLET, navigation.onBackupWallet)
                },
                onAdvancedSetting = {
                    openOrNavigate(NavigationTargets.ADVANCED_SETTING, navigation.onAdvancedSetting)
                },
                onChangeServer = {
                    openOrNavigate(NavigationTargets.CHANGE_SERVER, navigation.onChangeServer)
                },
                onAbout = {
                    openOrNavigate(NavigationTargets.ABOUT, navigation.onAbout)
                },
                onDaoHub = {
                    openOrNavigate(NavigationTargets.DAO_HUB, navigation.onDaoHub)
                },
                onTopUp = {
                    openOrNavigate(NavigationTargets.TOP_UP, navigation.onTopUp)
                },
            )

        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
            ) {
                SettingsView(
                    versionInfo = versionInfo,
                    navigation = foldNavigation,
                    onRescan = onReScan,
                )
            }
            if (expanded && main != null) {
                Box(
                    modifier =
                        Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outlineVariant),
                )
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                ) {
                    SettingsDetailPane(
                        activity = main,
                        route = selectedRoute,
                        onBack = { selectedRoute = null },
                        onOpenDao = onOpenDao,
                        onSetPin = onSetPin,
                        onDisablePin = onDisablePin,
                        versionInfo = versionInfo,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsDetailPane(
    activity: MainActivity,
    route: String?,
    onBack: () -> Unit,
    onOpenDao: (String) -> Unit,
    onSetPin: () -> Unit,
    onDisablePin: () -> Unit,
    versionInfo: VersionInfo,
) {
    when (route) {
        null -> SettingsCompanionHub(versionInfo = versionInfo)
        NavigationTargets.CHAT_SETTINGS ->
            activity.AndroidChatSettings(onBack = onBack)
        NavigationTargets.MESH_SETTINGS ->
            activity.AndroidMeshSettings(onBack = onBack)
        NavigationTargets.TOR_NETWORK_SETTINGS ->
            activity.AndroidTorNetworkSettings(onBack = onBack)
        NavigationTargets.SYNC_NOTIFICATION ->
            activity.AndroidSyncNotification(onBack = onBack)
        NavigationTargets.FIAT_CURRENCY ->
            activity.AndroidFiatCurrency(onBack = onBack)
        NavigationTargets.SECURITY ->
            activity.AndroidSecurity(
                onBack = onBack,
                onSetPin = onSetPin,
                onDisablePin = onDisablePin,
            )
        NavigationTargets.SETTING_BACK_UP_WALLET ->
            activity.AndroidSettingBackUpWallet(onBack = onBack)
        NavigationTargets.ABOUT ->
            activity.AndroidAboutView(onBack = onBack)
        NavigationTargets.ADVANCED_SETTING ->
            activity.AndroidAdvancedSetting(onBack = onBack)
        NavigationTargets.CHANGE_SERVER ->
            activity.AndroidChangeServer(onBack = onBack)
        NavigationTargets.DAO_HUB ->
            activity.AndroidDaoHub(
                onBack = onBack,
                onOpenDao = onOpenDao,
            )
        NavigationTargets.TOP_UP ->
            activity.AndroidTopUp(onBack = onBack)
        else -> SettingsCompanionHub(versionInfo = versionInfo)
    }
}
