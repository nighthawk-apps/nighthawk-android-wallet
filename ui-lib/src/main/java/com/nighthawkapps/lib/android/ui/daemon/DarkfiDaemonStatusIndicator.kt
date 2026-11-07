@file:Suppress("MagicNumber")

package com.nighthawkapps.lib.android.ui.daemon

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.sdk.daemon.DarkfiDaemonStatus
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme

@Composable
fun DarkfiDaemonStatusIndicator(
    status: DarkfiDaemonStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(daemonStatusContentDescriptionRes(status))
    Box(
        modifier =
            modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .semantics { contentDescription = label }
                .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(14.dp),
            shape = CircleShape,
            color = daemonStatusColor(status),
        ) {
            Box {}
        }
    }
}

@Composable
private fun daemonStatusColor(status: DarkfiDaemonStatus): Color =
    when (status) {
        DarkfiDaemonStatus.Connected -> Color(0xFF3DDC97)
        DarkfiDaemonStatus.Connecting -> Color(0xFFFFB340)
        DarkfiDaemonStatus.Starting -> MaterialTheme.colorScheme.primary
        DarkfiDaemonStatus.Reconnecting -> Color(0xFFFF9F43)
        DarkfiDaemonStatus.Stopped -> MaterialTheme.colorScheme.outline
        DarkfiDaemonStatus.Error -> WalletTheme.colors.dangerous
        DarkfiDaemonStatus.Unknown -> MaterialTheme.colorScheme.outline
        DarkfiDaemonStatus.Disabled -> MaterialTheme.colorScheme.outlineVariant
        DarkfiDaemonStatus.ReorgRecovery -> Color(0xFFFF9F43)
    }

private fun daemonStatusContentDescriptionRes(status: DarkfiDaemonStatus): Int =
    when (status) {
        DarkfiDaemonStatus.Connected -> R.string.daemon_status_connected
        DarkfiDaemonStatus.Connecting -> R.string.daemon_status_connecting
        DarkfiDaemonStatus.Starting -> R.string.daemon_status_starting
        DarkfiDaemonStatus.Reconnecting -> R.string.daemon_status_reconnecting
        DarkfiDaemonStatus.Stopped -> R.string.daemon_status_stopped
        DarkfiDaemonStatus.Error -> R.string.daemon_status_error
        DarkfiDaemonStatus.Unknown -> R.string.daemon_status_unknown
        DarkfiDaemonStatus.Disabled -> R.string.daemon_status_disabled
        DarkfiDaemonStatus.ReorgRecovery -> R.string.daemon_status_reorg_recovery
    }
