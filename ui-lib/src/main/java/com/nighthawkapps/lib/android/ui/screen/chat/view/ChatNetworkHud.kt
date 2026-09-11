package com.nighthawkapps.lib.android.ui.screen.chat.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.sdk.chat.hud.OutboundPeerSlot
import com.nighthawkapps.lib.android.sdk.chat.hud.OutboundPeerState
import com.nighthawkapps.lib.android.ui.R
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
    val connected = slots.any { it.state == OutboundPeerState.CONNECTED }
    NighthawkHudPanel(
        modifier = modifier.fillMaxWidth(),
        connectedGlow = connected,
    ) {
        Text(
            text = stringResource(R.string.ns_chat_hud_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.ns_chat_hud_subtitle),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TransportSegment(
            torSelected = torSelected,
            tcpLabel = stringResource(R.string.ns_chat_transport_tcp),
            torLabel = stringResource(R.string.ns_chat_transport_tor_short),
            onSelectTor = onSelectTor,
            enabled = transportEnabled,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            slots.forEach { slot ->
                PeerSlotRow(
                    slotIndex = slot.slot,
                    label = slot.displayUrl,
                    connected = slot.state == OutboundPeerState.CONNECTED,
                )
            }
        }
    }
}
