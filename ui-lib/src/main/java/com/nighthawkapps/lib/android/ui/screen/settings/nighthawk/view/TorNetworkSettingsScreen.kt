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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatPreferences
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.design.component.BodyMedium
import com.nighthawkapps.lib.android.ui.design.component.PrimaryButton
import com.nighthawkapps.lib.android.ui.design.component.TitleMedium
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme

@Composable
internal fun TorNetworkSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val prefs = remember { DarkfiChatPreferences(context) }

    var torRoute by remember { mutableStateOf(prefs.routeOutboundThroughTor) }
    var useEmbeddedTor by remember { mutableStateOf(prefs.useEmbeddedTor) }
    var socksHost by remember { mutableStateOf(prefs.socksHost) }
    var socksPortText by remember { mutableStateOf(prefs.socksPort.toString()) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(dimensionResource(id = R.dimen.screen_standard_margin)),
    ) {
        TorNetworkSettingsTopBar(onBack)
        TorNetworkSettingsIntro()
        TorWalletRoutingSwitchRow(
            checked = torRoute,
            onCheckedChange = {
                torRoute = it
                prefs.routeOutboundThroughTor = it
            },
        )
        TorEmbeddedBuiltinSwitchRow(
            checked = useEmbeddedTor,
            onCheckedChange = {
                useEmbeddedTor = it
                prefs.useEmbeddedTor = it
            },
        )
        Spacer(modifier = Modifier.height(12.dp))
        TorSocksHostField(
            socksHost = socksHost,
            onSocksHostChange = {
                socksHost = it
                prefs.socksHost = it
            },
        )
        Spacer(modifier = Modifier.height(8.dp))
        TorSocksPortField(
            socksPortText = socksPortText,
            onSocksPortTextChange = { raw ->
                if (raw.all { it.isDigit() } && raw.length <= 5) {
                    socksPortText = raw
                    raw.toIntOrNull()?.let { prefs.socksPort = it }
                }
            },
        )
        TorNetworkSettingsFooter(onBack = onBack)
    }
}

@Composable
private fun ColumnScope.TorNetworkSettingsTopBar(onBack: () -> Unit) {
    Column(modifier = Modifier.align(Alignment.Start)) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.receive_back_content_description),
            )
        }
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
                onCheckedChange = onCheckedChange,
            )
        }
        BodyMedium(
            text = stringResource(R.string.ns_tor_network_embedded_tor_desc),
            color = WalletTheme.colors.secondaryTitleText,
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun TorSocksHostField(
    socksHost: String,
    onSocksHostChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = socksHost,
        onValueChange = onSocksHostChange,
        label = { Text(stringResource(R.string.ns_chat_socks_host)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun TorSocksPortField(
    socksPortText: String,
    onSocksPortTextChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = socksPortText,
        onValueChange = onSocksPortTextChange,
        label = { Text(stringResource(R.string.ns_chat_socks_port)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
private fun TorNetworkSettingsFooter(onBack: () -> Unit,) {
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
