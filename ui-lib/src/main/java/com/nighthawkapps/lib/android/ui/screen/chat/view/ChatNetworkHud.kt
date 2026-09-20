package com.nighthawkapps.lib.android.ui.screen.chat.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.sdk.chat.hud.OutboundPeerSlot
import com.nighthawkapps.lib.android.sdk.chat.hud.OutboundPeerState
import com.nighthawkapps.lib.android.sdk.chat.hud.PeerHostDisplay
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.design.component.CommonTag
import com.nighthawkapps.lib.android.ui.design.component.NighthawkHudPanel
import com.nighthawkapps.lib.android.ui.design.component.PeerSlotRow
import com.nighthawkapps.lib.android.ui.design.component.TransportSegment

@Composable
internal fun ChatNetworkHud(
    slots: List<OutboundPeerSlot>,
    torSelected: Boolean,
    onSelectTor: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    transportEnabled: Boolean = true,
) {
    var showPeers by remember { mutableStateOf(false) }
    val connected = slots.any { it.state == OutboundPeerState.CONNECTED }
    NighthawkHudPanel(
        modifier = modifier.fillMaxWidth(),
        connectedGlow = connected,
        compact = true,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.ns_chat_hud_title),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.ns_chat_hud_peers),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier =
                    Modifier
                        .semantics { role = Role.Button }
                        .clickable { showPeers = true }
                        .testTag(CommonTag.HUD_PEERS_LINK)
                        .padding(horizontal = 2.dp, vertical = 2.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
            TransportSegment(
                torSelected = torSelected,
                tcpLabel = stringResource(R.string.ns_chat_transport_tcp),
                torLabel = stringResource(R.string.ns_chat_transport_tor_short),
                onSelectTor = onSelectTor,
                enabled = transportEnabled,
                compact = true,
            )
        }
    }
    if (showPeers) {
        PeerNamesDialog(
            slots = slots,
            onDismiss = { showPeers = false },
        )
    }
}

@Composable
private fun PeerNamesDialog(
    slots: List<OutboundPeerSlot>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(CommonTag.HUD_PEERS_DIALOG),
        title = { Text(stringResource(R.string.ns_chat_hud_peers)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (slots.none { !it.url.isNullOrBlank() }) {
                    Text(
                        text = stringResource(R.string.ns_chat_hud_peers_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                slots.forEach { slot ->
                    PeerSlotRow(
                        slotIndex = slot.slot,
                        label =
                            PeerHostDisplay.dnsName(
                                url = slot.url,
                                placeholder = slot.state.placeholderLabel,
                                peerIndex = slot.slot,
                            ),
                        connected = slot.state == OutboundPeerState.CONNECTED,
                        compact = true,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ns_done))
            }
        },
    )
}
