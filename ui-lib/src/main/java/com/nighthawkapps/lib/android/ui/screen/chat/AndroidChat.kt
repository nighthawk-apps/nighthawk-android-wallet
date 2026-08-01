package com.nighthawkapps.lib.android.ui.screen.chat

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nighthawkapps.lib.android.sdk.chat.ChatChannelMessage
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatConnectionState
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatController
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatPreferences
import com.nighthawkapps.lib.android.sdk.chat.EmbeddedDarkircNodeStatus
import com.nighthawkapps.lib.android.sdk.chat.darkirc.DarkircCryptoManager
import com.nighthawkapps.lib.android.sdk.chat.darkirc.DarkircDmPubkeyParser
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.SecureScreen
import com.nighthawkapps.lib.android.ui.common.setSensitivePlainText
import com.nighthawkapps.lib.android.ui.screen.chat.view.ChatNewDmSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CONNECTION_STATUS_MAX_LINES = 5

private enum class ChatInboxMode {
    Channels,
    Direct,
}

@Composable
internal fun MainActivity.AndroidChat(onChatSettings: () -> Unit) {
    val controller = LocalDarkfiChatController.current
    if (controller == null) {
        Text(
            text = stringResource(R.string.ns_chat_unavailable),
            modifier = Modifier.padding(24.dp),
        )
        return
    }
    ChatScreen(controller = controller, onChatSettings = onChatSettings)
}

@Composable
private fun ChatScreen(
    controller: DarkfiChatController,
    onChatSettings: () -> Unit,
) {
    val connectionState by controller.connectionState.collectAsStateWithLifecycle()
    val nodeStatus by controller.embeddedNodeStatus.collectAsStateWithLifecycle()
    val diagnostic by controller.diagnosticDetail.collectAsStateWithLifecycle()
    val messages by controller.messagesByChannel.collectAsStateWithLifecycle()
    val dmConversations by controller.dmConversations.collectAsStateWithLifecycle()

    var draft by remember { mutableStateOf("") }
    var inboxMode by remember { mutableStateOf(ChatInboxMode.Channels) }
    var selectedChannel by remember {
        mutableStateOf(controller.defaultChannels.firstOrNull() ?: "#dev")
    }
    var selectedDmLabel by remember { mutableStateOf<String?>(null) }
    var showNewDm by remember { mutableStateOf(false) }
    var newDmInitialPubkey by remember { mutableStateOf<String?>(null) }
    var messageMenuTarget by remember { mutableStateOf<ChatChannelMessage?>(null) }
    var deleteDmLabel by remember { mutableStateOf<String?>(null) }
    var sharePubkeyChannel by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val hasEmbedded = remember { DarkircCryptoManager.hasBundledDarkirc(context.applicationContext) }

    LaunchedEffect(Unit) {
        controller.refreshDmStateFromDisk()
        when (connectionState) {
            DarkfiChatConnectionState.Disconnected,
            DarkfiChatConnectionState.Error,
            DarkfiChatConnectionState.Degraded,
            -> controller.connectOrRetry()

            else -> Unit
        }
    }

    LaunchedEffect(connectionState, messages) {
        when (connectionState) {
            DarkfiChatConnectionState.ConnectedDirect,
            DarkfiChatConnectionState.ConnectedViaTor,
            -> {
                // Native embedded node syncs history automatically
            }

            else -> {
                Unit
            }
        }
    }

    val publicChannels =
        remember(messages, controller.defaultChannels) {
            (messages.keys.filter { it.startsWith("#") } + controller.defaultChannels)
                .distinct()
                // Keep upstream autojoin order for defaults, then any extras alphabetically.
                .sortedWith(
                    compareBy<String> { ch ->
                        val idx = controller.defaultChannels.indexOf(ch)
                        if (idx >= 0) idx else Int.MAX_VALUE
                    }.thenBy { it },
                )
        }

    val dmLabels =
        remember(dmConversations, messages) {
            val fromStore = dmConversations.map { it.contactLabel }
            val fromMsgs = messages.keys.filter { !it.startsWith("#") }
            (fromStore + fromMsgs).distinct().sorted()
        }

    val activeThreadKey =
        when (inboxMode) {
            ChatInboxMode.Channels -> selectedChannel
            ChatInboxMode.Direct -> selectedDmLabel ?: dmLabels.firstOrNull().orEmpty()
        }

    SecureScreen()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .imePadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.ns_chat_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                newDmInitialPubkey = null
                showNewDm = true
            }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.ns_chat_new_dm))
            }
            IconButton(onClick = onChatSettings) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.ns_chat_settings_short))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))

        CompactConnectionStatusBlock(
            nodeStatus = nodeStatus,
            connectionState = connectionState,
            diagnostic = diagnostic,
            nickname = controller.ircNickname,
            showRetry = connectionState == DarkfiChatConnectionState.Disconnected ||
                connectionState == DarkfiChatConnectionState.Error,
            onRetry = { controller.connectOrRetry() },
        )

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (inboxMode == ChatInboxMode.Channels && selectedChannel.startsWith("#")) {
                TextButton(
                    onClick = { sharePubkeyChannel = selectedChannel },
                    modifier = Modifier.heightIn(max = 32.dp),
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                    enabled = hasEmbedded,
                ) {
                    Text(
                        text = stringResource(R.string.ns_chat_share_pubkey_short),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = inboxMode == ChatInboxMode.Channels,
                onClick = { inboxMode = ChatInboxMode.Channels },
                label = { Text(stringResource(R.string.ns_chat_mode_channels)) },
            )
            FilterChip(
                selected = inboxMode == ChatInboxMode.Direct,
                onClick = {
                    inboxMode = ChatInboxMode.Direct
                    if (selectedDmLabel == null) {
                        selectedDmLabel = dmLabels.firstOrNull()
                    }
                },
                label = { Text(stringResource(R.string.ns_chat_mode_direct)) },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (inboxMode) {
            ChatInboxMode.Channels -> {
                Text(text = stringResource(R.string.ns_chat_channels_header), style = MaterialTheme.typography.labelLarge)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items = publicChannels, key = { it }) { ch ->
                        FilterChip(
                            selected = ch == selectedChannel,
                            onClick = { selectedChannel = ch },
                            label = { Text(text = ch) },
                        )
                    }
                }
            }

            ChatInboxMode.Direct -> {
                if (dmLabels.isEmpty()) {
                    Text(
                        text = stringResource(R.string.ns_chat_dm_list_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(items = dmLabels, key = { it }) { label ->
                            val meta = dmConversations.find { it.contactLabel == label }
                            FilterChip(
                                selected = label == selectedDmLabel,
                                onClick = { selectedDmLabel = label },
                                label = {
                                    Column {
                                        Text(text = label)
                                        meta?.lastMessagePreview?.let { preview ->
                                            Text(
                                                text = preview,
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                    selectedDmLabel?.let { label ->
                        TextButton(onClick = { deleteDmLabel = label }) {
                            Text(stringResource(R.string.ns_chat_dm_delete))
                        }
                    }
                }
                if (!hasEmbedded) {
                    Text(
                        text = stringResource(R.string.ns_chat_dm_requires_embedded),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        val listState = rememberLazyListState()
        val threadMessages = messages[activeThreadKey].orEmpty()

        // Auto-scroll to bottom when new messages arrive
        LaunchedEffect(threadMessages.size) {
            if (threadMessages.isNotEmpty()) {
                listState.animateScrollToItem(threadMessages.lastIndex)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(items = threadMessages, key = { it.eventId }) { line ->
                ChatMessageLine(
                    line = line,
                    onLongPress = { messageMenuTarget = line },
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
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions =
                    KeyboardActions(
                        onSend = {
                            val text = draft.trim()
                            if (text.isNotEmpty() && activeThreadKey.isNotBlank()) {
                                controller.sendToChannel(activeThreadKey, text)
                                draft = ""
                            }
                        }
                    ),
                enabled = activeThreadKey.isNotBlank(),
            )
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
                onClick = {
                    val text = draft.trim()
                    if (text.isNotEmpty() && activeThreadKey.isNotBlank()) {
                        controller.sendToChannel(activeThreadKey, text)
                        draft = ""
                    }
                },
                modifier = Modifier.heightIn(min = 48.dp),
                enabled = activeThreadKey.isNotBlank(),
            ) {
                Text(text = stringResource(R.string.ns_chat_send))
            }
        }

        if (connectionState == DarkfiChatConnectionState.Error ||
            connectionState == DarkfiChatConnectionState.Degraded
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.ns_chat_fix_network_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    if (showNewDm) {
        ChatNewDmSheet(
            controller = controller,
            initialPeerPublicKey = newDmInitialPubkey,
            onDismiss = { showNewDm = false },
            onStarted = { label ->
                inboxMode = ChatInboxMode.Direct
                selectedDmLabel = label
            },
        )
    }

    messageMenuTarget?.let { target ->
        val detectedKey = DarkircDmPubkeyParser.extractFromText(target.text)
        AlertDialog(
            onDismissRequest = { messageMenuTarget = null },
            title = { Text(target.nick) },
            text = { Text(target.text) },
            confirmButton = {
                Row {
                    TextButton(
                        onClick = {
                            scope.launch {
                                clipboard.setSensitivePlainText(context, target.text)
                                Toast
                                    .makeText(context, R.string.ns_chat_message_copied, Toast.LENGTH_SHORT)
                                    .show()
                            }
                            messageMenuTarget = null
                        },
                    ) {
                        Text(stringResource(R.string.ns_chat_copy_message))
                    }
                    if (detectedKey != null) {
                        TextButton(
                            onClick = {
                                messageMenuTarget = null
                                newDmInitialPubkey = detectedKey
                                showNewDm = true
                            },
                        ) {
                            Text(stringResource(R.string.ns_chat_dm_action_start))
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { messageMenuTarget = null }) {
                    Text(stringResource(R.string.ns_cancel))
                }
            },
        )
    }

    deleteDmLabel?.let { label ->
        AlertDialog(
            onDismissRequest = { deleteDmLabel = null },
            title = { Text(stringResource(R.string.ns_chat_dm_delete)) },
            text = { Text(stringResource(R.string.ns_chat_dm_delete_confirm, label)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteDmLabel = null
                        scope.launch {
                            val ok = controller.removeDmContact(label).isSuccess
                            withContext(Dispatchers.Main) {
                                Toast
                                    .makeText(
                                        context,
                                        if (ok) {
                                            R.string.ns_chat_dm_removed_ok
                                        } else {
                                            R.string.ns_chat_dm_added_failed
                                        },
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                if (selectedDmLabel == label) {
                                    selectedDmLabel = dmLabels.firstOrNull { it != label }
                                }
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.ns_chat_dm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDmLabel = null }) {
                    Text(stringResource(R.string.ns_cancel))
                }
            },
        )
    }

    sharePubkeyChannel?.let { channel ->
        AlertDialog(
            onDismissRequest = { sharePubkeyChannel = null },
            title = { Text(stringResource(R.string.ns_chat_dm_share_pubkey_title)) },
            text = { Text(stringResource(R.string.ns_chat_dm_share_pubkey_body, channel)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        sharePubkeyChannel = null
                        scope.launch {
                            val kp =
                                withContext(Dispatchers.IO) {
                                    controller.generateEphemeralDmShareKeypair()
                                }
                            if (kp != null) {
                                controller.sendToChannel(
                                    channel,
                                    DarkircDmPubkeyParser.formatShareLine(kp.myDmChachaPublicBase58),
                                )
                                Toast
                                    .makeText(
                                        context,
                                        R.string.ns_chat_dm_share_pubkey_ok,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.ns_chat_dm_share_pubkey_post))
                }
            },
            dismissButton = {
                TextButton(onClick = { sharePubkeyChannel = null }) {
                    Text(stringResource(R.string.ns_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatMessageLine(
    line: ChatChannelMessage,
    onLongPress: () -> Unit,
) {
    Text(
        text = "${line.nick}: ${line.text}",
        style = MaterialTheme.typography.bodyMedium,
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongPress,
                ),
    )
}

@Composable
private fun CompactConnectionStatusBlock(
    nodeStatus: EmbeddedDarkircNodeStatus,
    connectionState: DarkfiChatConnectionState,
    diagnostic: String?,
    nickname: String,
    showRetry: Boolean,
    onRetry: () -> Unit,
) {
    val diagLines =
        remember(diagnostic) {
            diagnostic
                ?.lineSequence()
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.take(CONNECTION_STATUS_MAX_LINES)
                ?.toList()
                .orEmpty()
        }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Match iOS ChatView: colored status dots are always visible (not swapped
        // out when diagnosticDetail is set — that was hiding the lights when connected).
        if (nodeStatus != EmbeddedDarkircNodeStatus.NotUsed) {
            StatusDotLine(
                dot = { EmbeddedDarkircNodeStatusIndicator(nodeStatus = nodeStatus, size = 8.dp) },
                label = embeddedNodeStatusShortLine(nodeStatus),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ChatConnectionStatusIndicator(ircState = connectionState, size = 8.dp)
            Text(
                text = ircStatusShortLine(connectionState),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (nickname.isNotBlank()) {
                Text(
                    text = nickname,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showRetry) {
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.heightIn(max = 32.dp),
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                ) {
                    Text(
                        text = stringResource(R.string.ns_chat_retry),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        Text(
            text = chatConnectionSummary(connectionState),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        diagLines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StatusDotLine(
    dot: @Composable () -> Unit,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        dot()
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

private fun embeddedNodeStatusShortLine(status: EmbeddedDarkircNodeStatus): String =
    when (status) {
        EmbeddedDarkircNodeStatus.NotUsed -> "Node: off"
        EmbeddedDarkircNodeStatus.Starting -> "Node: starting"
        EmbeddedDarkircNodeStatus.Running -> "Node: running"
        EmbeddedDarkircNodeStatus.MissingBinary -> "Node: missing binary"
        EmbeddedDarkircNodeStatus.Failed -> "Node: failed"
    }

private fun ircStatusShortLine(state: DarkfiChatConnectionState): String =
    when (state) {
        DarkfiChatConnectionState.Disconnected -> "IRC: disconnected"
        DarkfiChatConnectionState.Connecting -> "IRC: connecting"
        DarkfiChatConnectionState.ConnectedDirect -> "IRC: connected"
        DarkfiChatConnectionState.ConnectedViaTor -> "IRC: connected (Tor)"
        DarkfiChatConnectionState.Degraded -> "IRC: degraded"
        DarkfiChatConnectionState.Error -> "IRC: error"
    }

@Composable
private fun chatConnectionSummary(state: DarkfiChatConnectionState): String =
    stringResource(
        R.string.ns_chat_connection_line,
        torRoutingLabel(),
        chatConnectionLabel(state),
    )

@Composable
private fun torRoutingLabel(): String {
    val context = LocalContext.current.applicationContext
    val prefs = remember { DarkfiChatPreferences(context) }
    return stringResource(
        if (prefs.routeOutboundThroughTor) {
            R.string.ns_chat_transport_tor
        } else {
            R.string.ns_chat_transport_direct
        },
    )
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
