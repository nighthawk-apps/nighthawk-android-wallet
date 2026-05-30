package com.nighthawkapps.lib.android.ui.screen.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatConnectionState
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatController
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.R

@Composable
internal fun MainActivity.AndroidChat() {
    val controller = LocalDarkfiChatController.current
    if (controller == null) {
        Text(
            text = stringResource(R.string.ns_chat_unavailable),
            modifier = Modifier.padding(24.dp),
        )
        return
    }
    ChatScreen(controller = controller)
}

@Composable
private fun ChatScreen(controller: DarkfiChatController) {
    val connectionState by controller.connectionState.collectAsStateWithLifecycle()
    val diagnostic by controller.diagnosticDetail.collectAsStateWithLifecycle()
    val messages by controller.messagesByChannel.collectAsStateWithLifecycle()

    var draft by remember { mutableStateOf("") }
    var torEnabled by remember { mutableStateOf(controller.useTorTransport) }
    val transportLabel =
        if (torEnabled) {
            stringResource(R.string.ns_chat_transport_tor)
        } else {
            stringResource(R.string.ns_chat_transport_direct)
        }
    var selectedChannel by remember {
        mutableStateOf(controller.defaultChannels.firstOrNull() ?: "#dev")
    }

    LaunchedEffect(Unit) {
        controller.connectOrRetry()
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        Text(text = stringResource(R.string.ns_chat_title), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = stringResource(R.string.ns_chat_tor_toggle))
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = torEnabled,
                onCheckedChange = {
                    torEnabled = it
                    controller.useTorTransport = it
                    controller.connectOrRetry()
                },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = { controller.connectOrRetry() }) {
                Text(text = stringResource(R.string.ns_chat_retry))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text =
                    stringResource(
                        R.string.ns_chat_connection_line,
                        transportLabel,
                        chatConnectionLabel(connectionState),
                    ),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        diagnostic?.let { detail ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (connectionState == DarkfiChatConnectionState.Error) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(text = stringResource(R.string.ns_chat_channels_header), style = MaterialTheme.typography.labelLarge)

        val channels =
            remember(messages, controller.defaultChannels) {
                (messages.keys + controller.defaultChannels).distinct().sorted()
            }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = channels, key = { it }) { ch ->
                FilterChip(
                    selected = ch == selectedChannel,
                    onClick = { selectedChannel = ch },
                    label = { Text(text = ch) },
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(items = messages[selectedChannel].orEmpty()) { line ->
                Text(
                    text = "${line.nick}: ${line.text}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(text = stringResource(R.string.ns_chat_message_hint)) },
                singleLine = false,
                maxLines = 4,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    val text = draft.trim()
                    if (text.isNotEmpty()) {
                        controller.sendToChannel(selectedChannel, text)
                        draft = ""
                    }
                },
            ) {
                Text(text = stringResource(R.string.ns_chat_send))
            }
        }

        if (connectionState == DarkfiChatConnectionState.Error ||
            connectionState == DarkfiChatConnectionState.Degraded
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.ns_chat_fix_network_hint),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun chatConnectionLabel(state: DarkfiChatConnectionState): String =
    stringResource(
        when (state) {
            DarkfiChatConnectionState.Disconnected -> R.string.ns_chat_state_disconnected
            DarkfiChatConnectionState.Connecting -> R.string.ns_chat_state_connecting
            DarkfiChatConnectionState.ConnectedDirect -> R.string.ns_chat_state_connected_direct
            DarkfiChatConnectionState.ConnectedViaTor -> R.string.ns_chat_state_connected_tor
            DarkfiChatConnectionState.Degraded -> R.string.ns_chat_state_degraded
            DarkfiChatConnectionState.Error -> R.string.ns_chat_state_error
        },
    )
