package com.nighthawkapps.lib.android.ui.screen.settings.nighthawk.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.AlertDialog
import com.nighthawkapps.lib.android.ui.common.SettingsListItem
import com.nighthawkapps.lib.android.ui.common.VersionInfo
import com.nighthawkapps.lib.android.ui.design.component.TitleMedium
import com.nighthawkapps.lib.android.ui.design.theme.LocalCyberpunkChrome
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme
import com.nighthawkapps.lib.android.ui.fixture.VersionInfoFixture
import com.nighthawkapps.lib.android.ui.screen.settings.nighthawk.model.NighthawkSettingsNavigationCallbacks
import com.nighthawkapps.lib.android.ui.screen.settings.nighthawk.model.ReScanType
import com.nighthawkapps.lib.android.ui.screen.settings.nighthawk.share.ShareAppDialog

@Preview
@Composable
fun SettingsPreview() {
    WalletTheme(darkTheme = false) {
        Surface {
            SettingsView(
                versionInfo = VersionInfoFixture.new(),
                navigation =
                    NighthawkSettingsNavigationCallbacks(
                        onChatSettings = {},
                        onMeshSettings = {},
                        onTorNetworkSettings = {},
                        onSyncNotifications = {},
                        onFiatCurrency = {},
                        onSecurity = {},
                        onBackupWallet = {},
                        onAdvancedSetting = {},
                        onChangeServer = {},
                        onAbout = {},
                        onTopUp = {},
                    ),
                onRescan = {},
            )
        }
    }
}

@Composable
fun SettingsView(
    versionInfo: VersionInfo,
    navigation: NighthawkSettingsNavigationCallbacks,
    onRescan: (ReScanType) -> Unit,
) {
    var showSeedBackUpDialog by remember { mutableStateOf(false) }
    var showReScanDialog by remember { mutableStateOf(false) }
    var showShareAppDialog by remember { mutableStateOf(false) }

    val margin = dimensionResource(id = R.dimen.screen_standard_margin)
    val wordmarkClearance =
        if (LocalCyberpunkChrome.current) {
            dimensionResource(id = R.dimen.wallpaper_wordmark_clearance)
        } else {
            0.dp
        }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = margin,
                    end = margin,
                    bottom = margin,
                    top = margin + wordmarkClearance,
                ),
    ) {
        TitleMedium(text = stringResource(id = R.string.ns_settings).uppercase())
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 1.dp,
            color = WalletTheme.colors.divider,
        )
        SettingsHubChatSection(
            onChatSettings = navigation.onChatSettings,
            onMeshSettings = navigation.onMeshSettings,
        )
        SettingsHubNotificationsSection(navigation = navigation)
        SettingsHubWalletRowsUpper(
            navigation = navigation,
            onBackupTap = { showSeedBackUpDialog = true },
            onRescanTap = { showReScanDialog = true },
        )
        SettingsHubWalletRowsLower(
            versionInfo = versionInfo,
            navigation = navigation,
            onShareAppTap = { showShareAppDialog = true },
        )
        if (showShareAppDialog) {
            ShareAppDialog(onDismiss = { showShareAppDialog = false })
        }
        if (showSeedBackUpDialog) {
            AlertDialog(
                title = stringResource(id = R.string.ns_back_up_seed_dialog_title),
                desc = stringResource(id = R.string.ns_back_up_seed_dialog_body),
                confirmText = stringResource(id = R.string.ns_back_up_seed_dialog_positive),
                dismissText = stringResource(id = R.string.ns_cancel),
                onConfirm = {
                    showSeedBackUpDialog = false
                    navigation.onBackupWallet()
                },
                onDismiss = { showSeedBackUpDialog = false },
            )
        }
        if (showReScanDialog) {
            AlertDialog(
                title = stringResource(id = R.string.dialog_rescan_wallet_title),
                desc = stringResource(id = R.string.dialog_rescan_wallet_message),
                confirmText = stringResource(id = R.string.dialog_rescan_wallet_button_neutral).uppercase(),
                dismissText = stringResource(id = R.string.ns_cancel).uppercase(),
                onConfirm = {
                    showReScanDialog = false
                    onRescan(ReScanType.FULL_SCAN)
                },
                onDismiss = { showReScanDialog = false },
                onDismissRequest = { showReScanDialog = false },
            )
        }
    }
}

@Composable
private fun SettingsHubChatSection(
    onChatSettings: () -> Unit,
    onMeshSettings: () -> Unit,
) {
    Column {
        SettingsListItem(
            iconRes = R.drawable.ic_icon_chat,
            title = stringResource(id = R.string.ns_chat_settings_row_title),
            desc = stringResource(id = R.string.ns_chat_settings_row_desc),
            modifier =
                Modifier
                    .heightIn(min = dimensionResource(id = R.dimen.setting_list_item_min_height))
                    .clickable { onChatSettings() },
        )
        SettingsListItem(
            iconRes = R.drawable.ic_icon_connecting,
            title = stringResource(id = R.string.ns_mesh_settings_row_title),
            desc = stringResource(id = R.string.ns_mesh_settings_row_desc),
            modifier =
                Modifier
                    .heightIn(min = dimensionResource(id = R.dimen.setting_list_item_min_height))
                    .clickable { onMeshSettings() },
        )
    }
}

@Composable
private fun SettingsHubNotificationsSection(navigation: NighthawkSettingsNavigationCallbacks) {
    Column {
        SettingsListItem(
            iconRes = R.drawable.ic_icon_bell,
            title = stringResource(id = R.string.ns_sync_notifications),
            desc = stringResource(id = R.string.ns_sync_notifications_text),
            modifier =
                Modifier
                    .heightIn(min = dimensionResource(id = R.dimen.setting_list_item_min_height))
                    .clickable { navigation.onSyncNotifications() },
        )
        SettingsListItem(
            iconRes = R.drawable.ic_icon_fiat_currency,
            title = stringResource(id = R.string.ns_fiat_currency),
            desc = stringResource(id = R.string.ns_fiat_currency_text),
            modifier =
                Modifier
                    .heightIn(min = dimensionResource(id = R.dimen.setting_list_item_min_height))
                    .clickable { navigation.onFiatCurrency() },
        )
        SettingsListItem(
            iconRes = R.drawable.ic_icon_external_services,
            title = stringResource(id = R.string.ns_tor_network_settings_row_title),
            desc = stringResource(id = R.string.ns_tor_network_settings_row_desc),
            modifier =
                Modifier
                    .heightIn(min = dimensionResource(id = R.dimen.setting_list_item_min_height))
                    .clickable { navigation.onTorNetworkSettings() },
        )
    }
}

@Composable
private fun SettingsHubWalletRowsUpper(
    navigation: NighthawkSettingsNavigationCallbacks,
    onBackupTap: () -> Unit,
    onRescanTap: () -> Unit,
) {
    Column {
        SettingsListItem(
            iconRes = R.drawable.ic_icon_security,
            title = stringResource(id = R.string.ns_security),
            desc = stringResource(id = R.string.ns_security_text),
            modifier =
                Modifier
                    .heightIn(min = dimensionResource(id = R.dimen.setting_list_item_min_height))
                    .clickable { navigation.onSecurity() },
        )
        SettingsListItem(
            iconRes = R.drawable.ic_icon_backup,
            title = stringResource(id = R.string.ns_backup_wallet),
            desc = stringResource(id = R.string.ns_backup_wallet_text),
            modifier =
                Modifier
                    .heightIn(min = dimensionResource(id = R.dimen.setting_list_item_min_height))
                    .clickable { onBackupTap() },
        )
        SettingsListItem(
            iconRes = R.drawable.ic_icon_rescan_wallet,
            title = stringResource(id = R.string.ns_rescan_wallet),
            desc = stringResource(id = R.string.ns_rescan_wallet_text),
            modifier =
                Modifier
                    .heightIn(min = dimensionResource(id = R.dimen.setting_list_item_min_height))
                    .clickable { onRescanTap() },
        )
        SettingsListItem(
            iconRes = R.drawable.ic_icon_change_server,
            title = stringResource(id = R.string.ns_change_server),
            desc = stringResource(id = R.string.ns_change_server_text),
            modifier =
                Modifier
                    .heightIn(min = dimensionResource(id = R.dimen.setting_list_item_min_height))
                    .clickable { navigation.onChangeServer() },
        )
        SettingsListItem(
            iconRes = R.drawable.ic_icon_top_up,
            title = stringResource(id = R.string.ns_top_up),
            desc = stringResource(id = R.string.ns_top_up_text),
            modifier =
                Modifier
                    .heightIn(min = dimensionResource(id = R.dimen.setting_list_item_min_height))
                    .clickable { navigation.onTopUp() },
        )
    }
}

@Composable
private fun SettingsHubWalletRowsLower(
    versionInfo: VersionInfo,
    navigation: NighthawkSettingsNavigationCallbacks,
    onShareAppTap: () -> Unit,
) {
    Column {
        SettingsListItem(
            iconRes = R.drawable.ic_icon_transfer,
            title = stringResource(id = R.string.ns_share_app_row_title),
            desc = stringResource(id = R.string.ns_share_app_row_desc),
            modifier =
                Modifier
                    .heightIn(min = dimensionResource(id = R.dimen.setting_list_item_min_height))
                    .clickable { onShareAppTap() },
        )
        SettingsListItem(
            iconRes = R.drawable.ic_icon_settings,
            title = stringResource(id = R.string.advanced),
            desc = stringResource(id = R.string.advanced_msg),
            modifier =
                Modifier
                    .heightIn(min = dimensionResource(id = R.dimen.setting_list_item_min_height))
                    .clickable { navigation.onAdvancedSetting() },
        )
        SettingsListItem(
            iconRes = R.drawable.ic_icon_about,
            title = stringResource(id = R.string.ns_about),
            desc = stringResource(id = R.string.ns_about_text, versionInfo.versionName),
            modifier =
                Modifier
                    .heightIn(min = dimensionResource(id = R.dimen.setting_list_item_min_height))
                    .clickable { navigation.onAbout() },
        )
    }
}
