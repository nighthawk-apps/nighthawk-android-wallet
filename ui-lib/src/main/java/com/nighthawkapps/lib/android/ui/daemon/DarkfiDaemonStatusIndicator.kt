@file:Suppress("MagicNumber")

package com.nighthawkapps.lib.android.ui.daemon

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.sdk.daemon.DarkfiDaemonStatus
import com.nighthawkapps.lib.android.ui.R

@Composable
fun DarkfiDaemonStatusIndicator(
    status: DarkfiDaemonStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(daemonStatusContentDescriptionRes(status))
    Surface(
        modifier =
            modifier
                .size(14.dp)
                .semantics { contentDescription = label }
                .clickable(onClick = onClick),
        shape = CircleShape,
        color = daemonStatusColor(status),
    ) {
        Box {}
    }
}

@Composable
private fun daemonStatusColor(status: DarkfiDaemonStatus): Color =
    when (status) {
        DarkfiDaemonStatus.Connected -> Color(0xFF2E7D32)
        DarkfiDaemonStatus.Connecting -> Color(0xFFF9A825)
        DarkfiDaemonStatus.Starting -> Color(0xFF1976D2)
        DarkfiDaemonStatus.Reconnecting -> Color(0xFFEF6C00)
        DarkfiDaemonStatus.Stopped -> Color(0xFF9E9E9E)
        DarkfiDaemonStatus.Error -> Color(0xFFC62828)
        DarkfiDaemonStatus.Unknown -> MaterialTheme.colorScheme.outline
        DarkfiDaemonStatus.Disabled -> Color(0xFF757575)
        DarkfiDaemonStatus.ReorgRecovery -> Color(0xFFFF6F00) // deep amber
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
