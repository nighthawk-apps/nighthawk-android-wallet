@file:Suppress("MagicNumber")

package com.nighthawkapps.lib.android.ui.screen.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatConnectionState
import com.nighthawkapps.lib.android.sdk.chat.EmbeddedDarkircNodeStatus
import com.nighthawkapps.lib.android.ui.R

@Composable
fun ChatConnectionStatusIndicator(
    ircState: DarkfiChatConnectionState,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp,
) {
    val label = stringResource(chatIrcStatusContentDescriptionRes(ircState))
    Surface(
        modifier =
            modifier
                .size(size)
                .semantics { contentDescription = label },
        shape = CircleShape,
        color = chatIrcStatusColor(ircState),
    ) {
        Box {}
    }
}

@Composable
fun EmbeddedDarkircNodeStatusIndicator(
    nodeStatus: EmbeddedDarkircNodeStatus,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp,
) {
    val label = stringResource(embeddedNodeStatusContentDescriptionRes(nodeStatus))
    Surface(
        modifier =
            modifier
                .size(size)
                .semantics { contentDescription = label },
        shape = CircleShape,
        color = embeddedNodeStatusColor(nodeStatus),
    ) {
        Box {}
    }
}

fun chatIrcStatusColor(state: DarkfiChatConnectionState): Color =
    when (state) {
        DarkfiChatConnectionState.ConnectedDirect,
        DarkfiChatConnectionState.ConnectedViaTor,
        -> Color(0xFF2E7D32)

        DarkfiChatConnectionState.Connecting,
        DarkfiChatConnectionState.Degraded,
        -> Color(0xFFF9A825)

        DarkfiChatConnectionState.Disconnected,
        DarkfiChatConnectionState.Error,
        -> Color(0xFFC62828)
    }

fun embeddedNodeStatusColor(status: EmbeddedDarkircNodeStatus): Color =
    when (status) {
        EmbeddedDarkircNodeStatus.Running -> Color(0xFF2E7D32)

        EmbeddedDarkircNodeStatus.Starting -> Color(0xFFF9A825)

        EmbeddedDarkircNodeStatus.NotUsed -> Color(0xFF9E9E9E)

        EmbeddedDarkircNodeStatus.MissingBinary,
        EmbeddedDarkircNodeStatus.Failed,
        -> Color(0xFFC62828)
    }

private fun chatIrcStatusContentDescriptionRes(state: DarkfiChatConnectionState): Int =
    when (state) {
        DarkfiChatConnectionState.Disconnected -> R.string.ns_chat_status_irc_disconnected
        DarkfiChatConnectionState.Connecting -> R.string.ns_chat_status_irc_connecting
        DarkfiChatConnectionState.ConnectedDirect -> R.string.ns_chat_status_irc_connected_direct
        DarkfiChatConnectionState.ConnectedViaTor -> R.string.ns_chat_status_irc_connected_tor
        DarkfiChatConnectionState.Degraded -> R.string.ns_chat_status_irc_degraded
        DarkfiChatConnectionState.Error -> R.string.ns_chat_status_irc_error
    }

private fun embeddedNodeStatusContentDescriptionRes(status: EmbeddedDarkircNodeStatus): Int =
    when (status) {
        EmbeddedDarkircNodeStatus.NotUsed -> R.string.ns_chat_status_node_not_used
        EmbeddedDarkircNodeStatus.Starting -> R.string.ns_chat_status_node_starting
        EmbeddedDarkircNodeStatus.Running -> R.string.ns_chat_status_node_running
        EmbeddedDarkircNodeStatus.MissingBinary -> R.string.ns_chat_status_node_missing_binary
        EmbeddedDarkircNodeStatus.Failed -> R.string.ns_chat_status_node_failed
    }
