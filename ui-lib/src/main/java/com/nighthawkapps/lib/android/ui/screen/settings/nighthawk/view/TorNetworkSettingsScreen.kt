package com.nighthawkapps.lib.android.ui.screen.settings.nighthawk.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.global.AppWalletCoordinator
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatPreferences
import com.nighthawkapps.lib.android.sdk.tor.AppTorCoordinator
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.design.component.BodyMedium
import com.nighthawkapps.lib.android.ui.design.component.NighthawkTopBar
import com.nighthawkapps.lib.android.ui.design.component.NighthawkTopBarLeading
import com.nighthawkapps.lib.android.ui.design.component.PrimaryButton
import com.nighthawkapps.lib.android.ui.design.component.TitleMedium
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme
import kotlinx.coroutines.launch

@Composable
internal fun TorNetworkSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val prefs = remember { DarkfiChatPreferences(context) }
    val scope = rememberCoroutineScope()

    var torRoute by remember { mutableStateOf(prefs.routeOutboundThroughTor) }
    var useEmbeddedTor by remember { mutableStateOf(prefs.useEmbeddedTor) }
    var socksHost by remember { mutableStateOf(prefs.socksHost) }
    var socksPortText by remember { mutableStateOf(prefs.socksPort.toString()) }

    fun applyProfileChange() {
        scope.launch {
            AppTorCoordinator.applyNetworkProfileChange(context)
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WalletTheme.dimens.spacingDefault)
                .padding(vertical = dimensionResource(id = R.dimen.screen_standard_margin)),
    ) {
        TorNetworkSettingsTopBar(onBack)
        TorNetworkSettingsIntro()
        TorSectionHeading(stringResource(R.string.ns_tor_network_section_routing))
        BodyMedium(
            text = stringResource(R.string.ns_tor_network_section_routing_desc),
            color = WalletTheme.colors.secondaryTitleText,
        )
        Spacer(modifier = Modifier.height(8.dp))
        TorWalletRoutingSwitchRow(
            checked = torRoute,
            onCheckedChange = { enabled ->
                torRoute = enabled
                scope.launch {
                    AppTorCoordinator.applyTorRoutingEnabledWithWalletReload(
                        context,
                        enabled,
                        AppWalletCoordinator.get(context),
                    )
                }
            },
        )
        TorSectionDivider()
        TorSectionHeading(stringResource(R.string.ns_tor_network_section_builtin))
        BodyMedium(
            text = stringResource(R.string.ns_tor_network_section_builtin_desc),
            color = WalletTheme.colors.secondaryTitleText,
        )
        Spacer(modifier = Modifier.height(8.dp))
        TorEmbeddedBuiltinSwitchRow(
            checked = useEmbeddedTor,
            enabled = torRoute,
            onCheckedChange = { enabled ->
                useEmbeddedTor = enabled
                prefs.useEmbeddedTor = enabled
                applyProfileChange()
            },
        )
        TorSectionDivider()
        TorSectionHeading(stringResource(R.string.ns_tor_network_section_socks))
        BodyMedium(
            text =
                stringResource(
                    if (useEmbeddedTor && torRoute) {
                        R.string.ns_tor_network_section_socks_desc_embedded
                    } else {
                        R.string.ns_tor_network_section_socks_desc_external
                    },
                ),
            color = WalletTheme.colors.secondaryTitleText,
        )
        Spacer(modifier = Modifier.height(8.dp))
        TorSocksHostField(
            enabled = torRoute,
            socksHost = socksHost,
            onSocksHostChange = {
                socksHost = it
                prefs.socksHost = it
                applyProfileChange()
            },
        )
        Spacer(modifier = Modifier.height(8.dp))
        TorSocksPortField(
            enabled = torRoute,
            socksPortText = socksPortText,
            onSocksPortTextChange = { raw ->
                if (raw.all { it.isDigit() } && raw.length <= 5) {
                    socksPortText = raw
                    raw.toIntOrNull()?.let {
                        prefs.socksPort = it
                        applyProfileChange()
                    }
                }
            },
        )
        TorNetworkSettingsFooter(onBack = onBack)
    }
}

@Composable
private fun ColumnScope.TorNetworkSettingsTopBar(onBack: () -> Unit) {
    Column(modifier = Modifier.align(Alignment.Start)) {
        NighthawkTopBar(
            onLeadingClick = onBack,
            leading = NighthawkTopBarLeading.Back,
            title = stringResource(R.string.ns_tor_network_screen_title),
        )
        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.offset)))
    }
}

@Composable
private fun TorNetworkSettingsIntro() {
    Column {
        TitleMedium(text = stringResource(R.string.ns_tor_network_screen_title))
        Spacer(modifier = Modifier.height(8.dp))
        BodyMedium(
            text = stringResource(R.string.ns_tor_network_screen_body),
            color = WalletTheme.colors.secondaryTitleText,
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun TorSectionHeading(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
    )
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun TorSectionDivider() {
    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun TorWalletRoutingSwitchRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.ns_tor_network_wallet_toggle),
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun TorEmbeddedBuiltinSwitchRow(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.ns_tor_network_embedded_tor),
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
            )
        }
        if (!enabled) {
            BodyMedium(
                text = stringResource(R.string.ns_tor_network_embedded_tor_requires_routing),
                color = WalletTheme.colors.secondaryTitleText,
            )
        }
    }
}

@Composable
private fun TorSocksHostField(
    enabled: Boolean,
    socksHost: String,
    onSocksHostChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = socksHost,
        onValueChange = onSocksHostChange,
        enabled = enabled,
        label = { Text(stringResource(R.string.ns_chat_socks_host)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun TorSocksPortField(
    enabled: Boolean,
    socksPortText: String,
    onSocksPortTextChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = socksPortText,
        onValueChange = onSocksPortTextChange,
        enabled = enabled,
        label = { Text(stringResource(R.string.ns_chat_socks_port)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
private fun TorNetworkSettingsFooter(onBack: () -> Unit) {
    Column {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.ns_tor_network_footer_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        PrimaryButton(
            onClick = onBack,
            text = stringResource(R.string.ns_tor_network_done),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
