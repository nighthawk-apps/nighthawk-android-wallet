package com.nighthawkapps.lib.android.ui.screen.settings.nighthawk.model

data class NighthawkSettingsNavigationCallbacks(
    val onChatSettings: () -> Unit,
    val onMeshSettings: () -> Unit,
    val onTorNetworkSettings: () -> Unit,
    val onSyncNotifications: () -> Unit,
    val onFiatCurrency: () -> Unit,
    val onSecurity: () -> Unit,
    val onBackupWallet: () -> Unit,
    val onAdvancedSetting: () -> Unit,
    val onChangeServer: () -> Unit,
    val onAbout: () -> Unit,
    val onDaoHub: () -> Unit,
    val onTopUp: () -> Unit,
)
