package com.nighthawkapps.lib.android.ui.design.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

private val HudShape = RoundedCornerShape(8.dp)

/**
 * Charcoal glass HUD frame — DarkFi-like density, Nighthawk Stealth tokens
 * (surface + outline so Light/Midnight stay honest).
 */
@Composable
fun NighthawkHudPanel(
    modifier: Modifier = Modifier,
    connectedGlow: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val border = MaterialTheme.colorScheme.outline
    val glow = if (connectedGlow) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color.Transparent
    Surface(
        modifier =
            modifier
                .shadow(if (connectedGlow) 8.dp else 0.dp, HudShape, ambientColor = glow, spotColor = glow)
                .border(BorderStroke(1.dp, border), HudShape)
                .testTag(CommonTag.HUD_PANEL),
        shape = HudShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
fun PeerSlotRow(
    slotIndex: Int,
    label: String,
    connected: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .testTag("${CommonTag.HUD_PEER_SLOT}$slotIndex"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .background(
                        color =
                            if (connected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                        shape = CircleShape,
                    ),
        )
        Text(
            text = slotIndex.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 4.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color =
                if (connected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun TransportSegment(
    torSelected: Boolean,
    tcpLabel: String,
    torLabel: String,
    onSelectTor: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), HudShape)
                .testTag(CommonTag.HUD_TRANSPORT),
    ) {
        TransportChip(
            label = tcpLabel,
            selected = !torSelected,
            enabled = enabled,
            onClick = { onSelectTor(false) },
            modifier = Modifier.weight(1f),
        )
        TransportChip(
            label = torLabel,
            selected = torSelected,
            enabled = enabled,
            onClick = { onSelectTor(true) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TransportChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg =
        if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        } else {
            Color.Transparent
        }
    Box(
        modifier =
            modifier
                .background(bg)
                .clickable(enabled = enabled, onClick = onClick)
                .semantics {
                    role = Role.Tab
                    this.selected = selected
                }.heightIn(min = 44.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color =
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}
