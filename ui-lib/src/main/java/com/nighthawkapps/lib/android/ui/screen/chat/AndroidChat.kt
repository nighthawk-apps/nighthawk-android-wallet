package com.nighthawkapps.lib.android.ui.screen.chat

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nighthawkapps.lib.android.global.AppWalletCoordinator
import com.nighthawkapps.lib.android.global.DeepLinkUtil
import com.nighthawkapps.lib.android.sdk.chat.ChatChannelMessage
import com.nighthawkapps.lib.android.sdk.chat.ChatDeliveryLabel
import com.nighthawkapps.lib.android.sdk.chat.ChatDeliveryState
import com.nighthawkapps.lib.android.sdk.chat.ChatInvoices
import com.nighthawkapps.lib.android.sdk.chat.ChatLinkGuard
import com.nighthawkapps.lib.android.sdk.chat.ChatReceipt
import com.nighthawkapps.lib.android.sdk.chat.ChatReceiptCodec
import com.nighthawkapps.lib.android.sdk.chat.ChatReceiptPhase
import com.nighthawkapps.lib.android.sdk.chat.ChatReceiptStore
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatConnectionState
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatController
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatPreferences
import com.nighthawkapps.lib.android.sdk.chat.EmbeddedDarkircNodeStatus
import com.nighthawkapps.lib.android.sdk.chat.NighthawkContactStore
import com.nighthawkapps.lib.android.sdk.chat.darkirc.DarkircCryptoManager
import com.nighthawkapps.lib.android.sdk.chat.darkirc.DarkircDmPubkeyParser
import com.nighthawkapps.lib.android.sdk.chat.fud.FudOfferInbox
import com.nighthawkapps.lib.android.sdk.chat.fud.FudTransferDecision
import com.nighthawkapps.lib.android.sdk.chat.fud.FudTransferPolicy
import com.nighthawkapps.lib.android.sdk.chat.fud.FudUri
import com.nighthawkapps.lib.android.sdk.chat.hud.ChatChrome
import com.nighthawkapps.lib.android.sdk.chat.hud.ChatInlineSpan
import com.nighthawkapps.lib.android.sdk.chat.hud.ChatMessageLexer
import com.nighthawkapps.lib.android.sdk.chat.hud.ChatTimeline
import com.nighthawkapps.lib.android.sdk.chat.hud.ChatTimelineItem
import com.nighthawkapps.lib.android.sdk.mesh.NighthawkMeshService
import com.nighthawkapps.lib.android.sdk.net.LightwalletTlsPin
import com.nighthawkapps.lib.android.sdk.net.SessionTransport
import com.nighthawkapps.lib.android.sdk.nym.AppNymCoordinator
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.SecureScreen
import com.nighthawkapps.lib.android.ui.common.setSensitivePlainText
import com.nighthawkapps.lib.android.ui.design.theme.LocalCyberpunkChrome
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme
import com.nighthawkapps.lib.android.ui.preference.StandardPreferenceKeys
import com.nighthawkapps.lib.android.ui.preference.StandardPreferenceSingleton
import com.nighthawkapps.lib.android.ui.screen.chat.view.ChatNetworkHud
import com.nighthawkapps.lib.android.ui.screen.chat.view.ChatNewDmSheet
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.HomeViewModel
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.WalletViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CONNECTION_STATUS_MAX_LINES = 5

private fun receiptPhaseLabel(phase: ChatReceiptPhase): Int =
    when (phase) {
        ChatReceiptPhase.Pending -> R.string.ns_chat_receipt_pending
        ChatReceiptPhase.Broadcasted -> R.string.ns_chat_receipt_broadcasted
        ChatReceiptPhase.Confirmed -> R.string.ns_chat_receipt_confirmed
        ChatReceiptPhase.Failed -> R.string.ns_chat_receipt_failed
    }

private fun deliveryLabel(state: ChatDeliveryState): Int =
    when (state) {
        ChatDeliveryState.Queued -> R.string.ns_chat_delivery_queued
        ChatDeliveryState.Sending -> R.string.ns_chat_delivery_sending
        ChatDeliveryState.Relayed -> R.string.ns_chat_delivery_relayed
        ChatDeliveryState.Failed -> R.string.ns_chat_delivery_retry
    }

private enum class ChatInboxMode {
    Channels,
    Direct,
}

private sealed class FudPromptUi {
    data class Confirm(
        val uri: FudUri,
    ) : FudPromptUi()

    data class Info(
        val messageRes: Int,
        val arg: String? = null,
    ) : FudPromptUi()
}

@Composable
internal fun MainActivity.AndroidChat(
    onChatSettings: () -> Unit,
    onPayInvoice: (String) -> Unit,
) {
    val controller = LocalDarkfiChatController.current
    if (controller == null) {
        Text(
            text = stringResource(R.string.ns_chat_unavailable),
            modifier = Modifier.padding(24.dp),
        )
        return
    }
    val walletViewModel by viewModels<WalletViewModel>()
    val homeViewModel by viewModels<HomeViewModel>()
    val addresses by walletViewModel.addresses.collectAsStateWithLifecycle()
    val snapshot by walletViewModel.walletSnapshot.collectAsStateWithLifecycle()
    val pendingThread by homeViewModel.pendingChatThreadFlow.collectAsStateWithLifecycle()
    ChatScreen(
        controller = controller,
        onChatSettings = onChatSettings,
        onPayInvoice = onPayInvoice,
        receiveAddress = addresses?.privateAddresses?.firstOrNull().orEmpty(),
        chainTip = snapshot?.processorInfo?.chainTip ?: 0L,
        syncStatusMessage = snapshot?.syncStatusMessage.orEmpty(),
        pendingThread = pendingThread,
        onConsumeThread = { homeViewModel.pendingChatThread = null },
        onBeginReceipt = { homeViewModel.pendingChatReceiptId = it },
    )
}

@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
private fun ChatScreen(
    controller: DarkfiChatController,
    onChatSettings: () -> Unit,
    onPayInvoice: (String) -> Unit,
    receiveAddress: String,
    chainTip: Long,
    syncStatusMessage: String,
    pendingThread: String?,
    onConsumeThread: () -> Unit,
    onBeginReceipt: (String) -> Unit,
) {
    val connectionState by controller.connectionState.collectAsStateWithLifecycle()
    val nodeStatus by controller.embeddedNodeStatus.collectAsStateWithLifecycle()
    val diagnostic by controller.diagnosticDetail.collectAsStateWithLifecycle()
    val messages by controller.messagesByChannel.collectAsStateWithLifecycle()
    val dmConversations by controller.dmConversations.collectAsStateWithLifecycle()

    var draft by remember { mutableStateOf("") }
    val drafts = remember { mutableMapOf<String, String>() }
    var inboxMode by remember { mutableStateOf(ChatInboxMode.Channels) }
    var selectedChannel by remember {
        mutableStateOf(controller.defaultChannels.firstOrNull() ?: "#nighthawk")
    }
    var selectedDmLabel by remember { mutableStateOf<String?>(null) }

    fun roomKey(
        mode: ChatInboxMode,
        channel: String,
        dm: String?
    ): String = ChatDrafts.key(mode == ChatInboxMode.Direct, channel, dm)

    fun openRoom(update: () -> Unit) {
        val previous = roomKey(inboxMode, selectedChannel, selectedDmLabel)
        val current = draft
        update()
        draft =
            ChatDrafts.switch(
                drafts,
                previous,
                roomKey(inboxMode, selectedChannel, selectedDmLabel),
                current,
            )
    }

    fun clearDraft() {
        ChatDrafts.clear(drafts, roomKey(inboxMode, selectedChannel, selectedDmLabel))
        draft = ""
    }
    var showNewDm by remember { mutableStateOf(false) }
    var newDmInitialPubkey by remember { mutableStateOf<String?>(null) }
    var messageMenuTarget by remember { mutableStateOf<ChatChannelMessage?>(null) }
    var deleteDmLabel by remember { mutableStateOf<String?>(null) }
    var sharePubkeyChannel by remember { mutableStateOf<String?>(null) }
    var showNetworkHud by remember { mutableStateOf(false) }

    val outboundSlots by controller.outboundSlots.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val hasEmbedded = remember { DarkircCryptoManager.hasBundledDarkirc(context.applicationContext) }
    var meshOn by remember { mutableStateOf(false) }
    var tlsPinSummary by remember { mutableStateOf<String?>(null) }
    val chatPrefs = remember { DarkfiChatPreferences(context.applicationContext) }
    var selectedPrivacyNetwork by remember { mutableStateOf(SessionTransport.effective(chatPrefs)) }
    LaunchedEffect(showNetworkHud) {
        if (showNetworkHud) {
            selectedPrivacyNetwork = SessionTransport.effective(chatPrefs)
            withContext(Dispatchers.IO) {
                controller.refreshOutboundSlots()
                tlsPinSummary = formatTlsPinSummary(LightwalletTlsPin.pinBytesOrNull(context))
            }
        }
    }
    var threadDisplayNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var localDisplayName by remember { mutableStateOf("") }
    var pendingPublicInvoice by remember { mutableStateOf<String?>(null) }
    var pendingClearnetUrl by remember { mutableStateOf<String?>(null) }
    var showDrkComposer by remember { mutableStateOf(false) }
    var drkAmount by remember { mutableStateOf("") }
    var drkMemo by remember { mutableStateOf("") }
    var drkAddress by remember { mutableStateOf("") }
    var fudPrompt by remember { mutableStateOf<FudPromptUi?>(null) }

    LaunchedEffect(Unit) {
        val prefs = StandardPreferenceSingleton.getInstance(context.applicationContext)
        meshOn = StandardPreferenceKeys.IS_NIGHTHAWK_MESH_ENABLED.getValue(prefs)
        threadDisplayNames = ChatThreadDisplayNames.load(context)
        launch {
            StandardPreferenceKeys.IS_NIGHTHAWK_MESH_ENABLED.observe(prefs).collect { meshOn = it }
        }
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
            DarkfiChatConnectionState.ConnectedViaNym,
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
    LaunchedEffect(pendingThread) {
        val thread = pendingThread?.trim().orEmpty()
        if (thread.isEmpty()) return@LaunchedEffect
        if (thread.startsWith("#")) {
            inboxMode = ChatInboxMode.Channels
            selectedChannel = thread
        } else {
            inboxMode = ChatInboxMode.Direct
            selectedDmLabel = thread
        }
        onConsumeThread()
    }
    LaunchedEffect(activeThreadKey) {
        localDisplayName = threadDisplayNames[activeThreadKey].orEmpty()
    }

    val isEncryptedThread =
        ChatChrome.threadIsEncrypted(
            isDirectInbox = inboxMode == ChatInboxMode.Direct,
            threadKey = activeThreadKey,
        )
    val myNick = controller.ircNickname

    SecureScreen()

    val cyberpunkChrome = LocalCyberpunkChrome.current
    // Wallhaven magenta, translucent so Channels / Direct / #channel chips don't dominate.
    val chatSelectionColors =
        if (cyberpunkChrome) {
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(0xFF6B1A6B).copy(alpha = 0.38f),
                selectedLabelColor = Color(0xFFFFD6F6),
                selectedLeadingIconColor = Color(0xFFC94BB8),
            )
        } else {
            FilterChipDefaults.filterChipColors()
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    start = 12.dp,
                    end = 12.dp,
                    top = if (cyberpunkChrome) dimensionResource(id = R.dimen.wallpaper_wordmark_clearance) else 8.dp,
                    bottom = 4.dp,
                ).imePadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = inboxMode == ChatInboxMode.Channels,
                onClick = { openRoom { inboxMode = ChatInboxMode.Channels } },
                label = { Text(stringResource(R.string.ns_chat_mode_channels)) },
                colors = chatSelectionColors,
            )
            FilterChip(
                selected = inboxMode == ChatInboxMode.Direct,
                onClick = {
                    openRoom {
                        inboxMode = ChatInboxMode.Direct
                        if (selectedDmLabel == null) {
                            selectedDmLabel = dmLabels.firstOrNull()
                        }
                    }
                },
                label = { Text(stringResource(R.string.ns_chat_mode_direct)) },
                colors = chatSelectionColors,
            )
        }
        // Own row so five IconButtons are not squeezed beside the chips on a phone.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                newDmInitialPubkey = null
                showNewDm = true
            }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.ns_chat_new_dm))
            }
            IconButton(
                onClick = {
                    val uri = DeepLinkUtil.buildPaymentRequestUri(receiveAddress) ?: return@IconButton
                    if (inboxMode == ChatInboxMode.Channels && activeThreadKey.startsWith("#")) {
                        pendingPublicInvoice = uri
                    } else if (activeThreadKey.isNotBlank()) {
                        controller.sendToChannel(activeThreadKey, uri)
                    }
                },
                enabled = receiveAddress.isNotBlank() && activeThreadKey.isNotBlank(),
            ) {
                Icon(
                    Icons.Filled.RequestQuote,
                    contentDescription = stringResource(R.string.ns_chat_attach_request),
                )
            }
            IconButton(
                onClick = {
                    val fromDraft = draft.trim().takeIf { it.startsWith("drk:") }
                    val fromHistory =
                        messages[activeThreadKey]
                            .orEmpty()
                            .asReversed()
                            .firstOrNull { it.text.startsWith("drk:") }
                            ?.text
                    val uri = fromDraft ?: fromHistory
                    if (uri != null && DeepLinkUtil.getSendDeepLinkData(Uri.parse(uri)) != null) {
                        onPayInvoice(uri)
                    }
                },
                enabled = activeThreadKey.isNotBlank(),
            ) {
                Icon(
                    Icons.Filled.Payment,
                    contentDescription = stringResource(R.string.ns_chat_pay_invoice),
                )
            }
            IconButton(onClick = { showNetworkHud = true }) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = stringResource(R.string.ns_chat_hud_title),
                )
            }
            IconButton(onClick = onChatSettings) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.ns_chat_settings_short))
            }
        }
        if (showNetworkHud) {
            AlertDialog(
                onDismissRequest = { showNetworkHud = false },
                title = { Text(stringResource(R.string.ns_chat_hud_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val privacyRoute =
                            when (connectionState) {
                                DarkfiChatConnectionState.ConnectedViaNym -> "Nym mixnet"
                                DarkfiChatConnectionState.ConnectedViaTor -> "Tor"
                                DarkfiChatConnectionState.ConnectedDirect -> "Direct (clearnet)"
                                else -> connectionState.name
                            }
                        Text(
                            text = "Active privacy route: $privacyRoute",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        CompactConnectionStatusBlock(
                            nodeStatus = nodeStatus,
                            connectionState = connectionState,
                            diagnostic = diagnostic,
                            nickname = controller.ircNickname,
                            showRetry =
                                connectionState == DarkfiChatConnectionState.Disconnected ||
                                    connectionState == DarkfiChatConnectionState.Error,
                            onRetry = { controller.connectOrRetry() },
                            onToggleHud = {},
                        )
                        ChatNetworkHud(
                            slots = outboundSlots,
                            selectedNetwork = selectedPrivacyNetwork,
                            onSelectNetwork = { network ->
                                selectedPrivacyNetwork = network
                                AppNymCoordinator.applyPrivacyNetworkAsync(
                                    context.applicationContext,
                                    network,
                                    AppWalletCoordinator.get(context.applicationContext),
                                )
                            },
                        )
                        val connectedSlots = outboundSlots.count { it.state.name == "CONNECTED" }
                        Text(
                            text = "Outbound peers: $connectedSlots / ${outboundSlots.size}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        if (meshOn) {
                            Text(
                                text = stringResource(R.string.ns_mesh_on_badge),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        Text(
                            text = stringResource(R.string.ns_chat_diag_route, connectionState.name),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            text =
                                if (chainTip > 0L) {
                                    stringResource(R.string.ns_chat_diag_height, chainTip)
                                } else {
                                    stringResource(R.string.ns_chat_diag_height_unknown)
                                },
                            style = MaterialTheme.typography.labelSmall,
                        )
                        if (syncStatusMessage.isNotBlank()) {
                            Text(
                                text = stringResource(R.string.ns_chat_diag_sync_status, syncStatusMessage),
                                style = MaterialTheme.typography.labelSmall,
                                color =
                                    if (syncStatusMessage.contains("stale", ignoreCase = true) ||
                                        syncStatusMessage.contains("error", ignoreCase = true)
                                    ) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                        }
                        Text(
                            text =
                                tlsPinSummary?.let { pin ->
                                    stringResource(R.string.ns_chat_diag_tls_pin, pin)
                                } ?: stringResource(R.string.ns_chat_diag_tls_unpinned),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            text = stringResource(R.string.ns_chat_diag_mesh, NighthawkMeshService.hudPeerCount),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            text =
                                stringResource(
                                    if (isEncryptedThread) {
                                        R.string.ns_chat_direct_e2e
                                    } else {
                                        R.string.ns_chat_public_channel
                                    },
                                ),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showNetworkHud = false }) {
                        Text(stringResource(R.string.ns_done))
                    }
                },
            )
        }

        if (inboxMode == ChatInboxMode.Channels && selectedChannel.startsWith("#")) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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

        when (inboxMode) {
            ChatInboxMode.Channels -> {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items = publicChannels, key = { it }) { ch ->
                        FilterChip(
                            selected = ch == selectedChannel,
                            onClick = { openRoom { selectedChannel = ch } },
                            label = { Text(text = ch) },
                            colors = chatSelectionColors,
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
                                onClick = { openRoom { selectedDmLabel = label } },
                                label = {
                                    Column {
                                        Text(text = ChatThreadDisplayNames.display(threadDisplayNames, label))
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
                                colors = chatSelectionColors,
                            )
                        }
                    }
                    selectedDmLabel?.let { label ->
                        TextButton(onClick = { deleteDmLabel = label }) {
                            Text(stringResource(R.string.ns_chat_dm_delete))
                        }
                    }
                    if (activeThreadKey.isNotBlank() && inboxMode == ChatInboxMode.Direct) {
                        OutlinedTextField(
                            value = localDisplayName,
                            onValueChange = {
                                localDisplayName = it
                                scope.launch {
                                    ChatThreadDisplayNames.put(context, activeThreadKey, it)
                                    threadDisplayNames = ChatThreadDisplayNames.load(context)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.ns_chat_local_display_name)) },
                            placeholder = { Text(stringResource(R.string.ns_chat_local_display_name_hint)) },
                            singleLine = true,
                        )
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
        val timeline = remember(threadMessages) { ChatTimeline.group(threadMessages) }
        val shouldAutoScroll by remember(listState) {
            derivedStateOf {
                val info = listState.layoutInfo
                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                shouldAutoScrollOnNewMessage(lastVisible, info.totalItemsCount)
            }
        }

        // Scroll to bottom on initial load and when switching channels/DM threads.
        LaunchedEffect(activeThreadKey) {
            if (timeline.isNotEmpty()) {
                listState.scrollToItem(timeline.lastIndex)
            }
        }

        // Auto-scroll to bottom only when already near bottom or sending our own message.
        LaunchedEffect(timeline.size) {
            if (timeline.isEmpty()) return@LaunchedEffect
            val lastMessage = (timeline.lastOrNull() as? ChatTimelineItem.Message)?.message
            val isOwnNewMessage =
                lastMessage != null &&
                    (lastMessage.eventId.startsWith("local-") || lastMessage.eventId.startsWith("queued-"))
            if (shouldAutoScroll || isOwnNewMessage) {
                listState.animateScrollToItem(timeline.lastIndex)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(
                items = timeline,
                key = { index, item ->
                    when (item) {
                        // Index-qualified: out-of-order DAG can insert duplicate
                        // epochDay separators and crash LazyColumn on key collision.
                        is ChatTimelineItem.DateSeparator -> "day-${item.epochDay}-$index"

                        is ChatTimelineItem.Message -> item.message.eventId
                    }
                },
            ) { _, item ->
                when (item) {
                    is ChatTimelineItem.DateSeparator -> {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    is ChatTimelineItem.Message -> {
                        ChatMessageLine(
                            line = item.message,
                            myNick = myNick,
                            isEncryptedThread = isEncryptedThread,
                            onLongPress = { messageMenuTarget = item.message },
                            onPayInvoice = onPayInvoice,
                            onClearnetLink = { url -> pendingClearnetUrl = url },
                            onRetry = {
                                controller.sendToChannel(item.message.channel, item.message.text)
                            },
                            onFudTap = { raw ->
                                val uri = FudUri.parse(raw)
                                when (
                                    FudTransferPolicy.decide(
                                        enabled = chatPrefs.allowFudTransfers,
                                        torReady =
                                            connectionState == DarkfiChatConnectionState.ConnectedViaTor ||
                                                connectionState == DarkfiChatConnectionState.ConnectedViaNym,
                                        meshOn = meshOn,
                                        uri = uri,
                                    )
                                ) {
                                    FudTransferDecision.Allowed -> {
                                        uri?.let { fudPrompt = FudPromptUi.Confirm(it) }
                                    }

                                    FudTransferDecision.Disabled -> {
                                        fudPrompt = FudPromptUi.Info(R.string.ns_chat_fud_disabled)
                                    }

                                    FudTransferDecision.NeedsPrivateTransport -> {
                                        fudPrompt = FudPromptUi.Info(R.string.ns_chat_fud_needs_transport)
                                    }

                                    FudTransferDecision.Invalid -> {
                                        fudPrompt = FudPromptUi.Info(R.string.ns_chat_fud_invalid)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            TextButton(
                onClick = {
                    val dmLabel = selectedDmLabel
                    scope.launch {
                        drkAddress =
                            if (dmLabel != null) {
                                withContext(Dispatchers.IO) {
                                    NighthawkContactStore.findByNick(context, dmLabel)?.walletAddress
                                }.orEmpty()
                            } else {
                                ""
                            }
                        showDrkComposer = true
                    }
                },
                enabled = activeThreadKey.isNotBlank(),
            ) {
                Text(stringResource(R.string.ns_chat_send_drk))
            }
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
                                clearDraft()
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
                        clearDraft()
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

    fun postChatPayment(tip: Boolean) {
        val address = drkAddress.trim()
        if (address.isEmpty() || activeThreadKey.isBlank()) return
        val memo = drkMemo.trim().ifBlank { if (tip) "Tip" else null }
        val uri = DeepLinkUtil.buildPaymentRequestUri(address, drkAmount.ifBlank { null }, memo) ?: return
        val receipt =
            ChatReceipt(
                id =
                    java.util.UUID
                        .randomUUID()
                        .toString(),
                phase = ChatReceiptPhase.Pending,
                amount = drkAmount.ifBlank { "0" },
                address = address,
                memo = memo,
                txId = null,
            )
        ChatReceiptStore.upsert(context, receipt)
        onBeginReceipt(receipt.id)
        controller.sendToChannel(activeThreadKey, ChatReceiptCodec.encode(receipt))
        showDrkComposer = false
        onPayInvoice(uri)
    }

    if (showDrkComposer) {
        AlertDialog(
            onDismissRequest = { showDrkComposer = false },
            title = { Text(stringResource(R.string.ns_chat_send_drk)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = drkAmount,
                        onValueChange = { drkAmount = it },
                        label = { Text(stringResource(R.string.ns_request_amount_hint)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = drkMemo,
                        onValueChange = { drkMemo = it },
                        label = { Text(stringResource(R.string.ns_request_memo_hint)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = drkAddress,
                        onValueChange = { drkAddress = it },
                        label = { Text(stringResource(R.string.ns_chat_drk_address)) },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                Row {
                    TextButton(
                        onClick = {
                            val uri =
                                DeepLinkUtil.buildPaymentRequestUri(
                                    receiveAddress,
                                    drkAmount,
                                    drkMemo,
                                )
                            showDrkComposer = false
                            if (uri == null) return@TextButton
                            if (inboxMode == ChatInboxMode.Channels && activeThreadKey.startsWith("#")) {
                                pendingPublicInvoice = uri
                            } else if (activeThreadKey.isNotBlank()) {
                                controller.sendToChannel(activeThreadKey, uri)
                            }
                        },
                    ) {
                        Text(stringResource(R.string.ns_chat_attach_request))
                    }
                    TextButton(onClick = { postChatPayment(tip = false) }) {
                        Text(stringResource(R.string.ns_chat_send))
                    }
                    TextButton(onClick = { postChatPayment(tip = true) }) {
                        Text(stringResource(R.string.ns_chat_tip))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDrkComposer = false }) {
                    Text(stringResource(R.string.ns_cancel))
                }
            },
        )
    }

    if (pendingPublicInvoice != null) {
        AlertDialog(
            onDismissRequest = { pendingPublicInvoice = null },
            title = { Text(stringResource(R.string.ns_chat_attach_request)) },
            text = { Text(stringResource(R.string.ns_chat_public_pay_warning)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = pendingPublicInvoice
                        pendingPublicInvoice = null
                        if (uri != null && activeThreadKey.isNotBlank()) {
                            controller.sendToChannel(activeThreadKey, uri)
                        }
                    },
                ) {
                    Text(stringResource(R.string.ns_chat_public_pay_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPublicInvoice = null }) {
                    Text(stringResource(R.string.ns_cancel))
                }
            },
        )
    }

    pendingClearnetUrl?.let { url ->
        AlertDialog(
            onDismissRequest = { pendingClearnetUrl = null },
            title = { Text(stringResource(R.string.ns_chat_clearnet_link_title)) },
            text = { Text(stringResource(R.string.ns_chat_clearnet_link_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingClearnetUrl = null
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    },
                ) {
                    Text(stringResource(R.string.ns_chat_clearnet_open_browser))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingClearnetUrl = null
                        scope.launch(Dispatchers.Main) {
                            clipboard.setSensitivePlainText(context, url)
                            Toast
                                .makeText(context, R.string.ns_copied, Toast.LENGTH_SHORT)
                                .show()
                        }
                    },
                ) {
                    Text(stringResource(R.string.ns_chat_clearnet_copy_link))
                }
            },
        )
    }

    if (showNewDm) {
        ChatNewDmSheet(
            controller = controller,
            initialPeerPublicKey = newDmInitialPubkey,
            onDismiss = { showNewDm = false },
            onStarted = { label ->
                openRoom {
                    inboxMode = ChatInboxMode.Direct
                    selectedDmLabel = label
                }
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
                                    openRoom {
                                        selectedDmLabel = dmLabels.firstOrNull { it != label }
                                    }
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

    fudPrompt?.let { prompt ->
        when (prompt) {
            is FudPromptUi.Confirm -> {
                AlertDialog(
                    onDismissRequest = { fudPrompt = null },
                    title = { Text(stringResource(R.string.ns_chat_fud_confirm_title)) },
                    text = {
                        Text(stringResource(R.string.ns_chat_fud_confirm_body, prompt.uri.displayName))
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                runCatching {
                                    FudOfferInbox.queue(context.applicationContext.filesDir, prompt.uri)
                                }.onSuccess {
                                    fudPrompt =
                                        FudPromptUi.Info(
                                            R.string.ns_chat_fud_queued,
                                            prompt.uri.displayName,
                                        )
                                }.onFailure {
                                    fudPrompt = FudPromptUi.Info(R.string.ns_chat_fud_queue_failed)
                                }
                            },
                        ) {
                            Text(stringResource(R.string.ns_chat_fud_queue))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { fudPrompt = null }) {
                            Text(stringResource(R.string.ns_cancel))
                        }
                    },
                )
            }

            is FudPromptUi.Info -> {
                AlertDialog(
                    onDismissRequest = { fudPrompt = null },
                    title = { Text(stringResource(R.string.ns_chat_fud_confirm_title)) },
                    text = {
                        Text(
                            if (prompt.arg != null) {
                                stringResource(prompt.messageRes, prompt.arg)
                            } else {
                                stringResource(prompt.messageRes)
                            },
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { fudPrompt = null }) {
                            Text(stringResource(android.R.string.ok))
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatMessageLine(
    line: ChatChannelMessage,
    myNick: String,
    isEncryptedThread: Boolean,
    onLongPress: () -> Unit,
    onPayInvoice: (String) -> Unit,
    onClearnetLink: (String) -> Unit,
    onRetry: () -> Unit,
    onFudTap: (String) -> Unit,
) {
    // Trust FFI isOutgoing only — nick string compare is spoofable (Fable 5.1 Fix 3).
    val isOwn = line.isOutgoing
    val surfaceColor = MaterialTheme.colorScheme.surface
    val isDarkSurface =
        (surfaceColor.red * 0.299f + surfaceColor.green * 0.587f + surfaceColor.blue * 0.114f) < 0.5f
    val nickColor = Color(ChatChrome.nickArgb(line.nick, myNick, dark = isDarkSurface))
    val bodyColor =
        if (isEncryptedThread) {
            if (isOwn) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        } else if (isOwn) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onBackground
        }
    val linkColor = MaterialTheme.colorScheme.primary
    val mutedColor = WalletTheme.colors.secondaryTitleText
    val time = ChatTimeline.gutterTime(line.timestampMs)
    val spans = remember(line.text) { ChatMessageLexer.lex(line.text) }
    val annotated =
        remember(line.nick, line.text, spans, nickColor, bodyColor, linkColor, mutedColor) {
            buildAnnotatedString {
                val nickStart = length
                append("${line.nick}: ")
                addStyle(SpanStyle(color = nickColor), nickStart, length)
                for (span in spans) {
                    when (span) {
                        is ChatInlineSpan.Text -> {
                            val start = length
                            append(span.value)
                            addStyle(SpanStyle(color = bodyColor), start, length)
                        }

                        is ChatInlineSpan.Url -> {
                            val start = length
                            append(span.url)
                            if (ChatLinkGuard.isClearnetHttp(span.url)) {
                                addLink(
                                    LinkAnnotation.Clickable(
                                        tag = "clearnet:${span.url}",
                                        styles =
                                            TextLinkStyles(
                                                SpanStyle(
                                                    color = linkColor,
                                                    textDecoration = TextDecoration.Underline,
                                                ),
                                            ),
                                        linkInteractionListener = {
                                            onClearnetLink(span.url)
                                        },
                                    ),
                                    start,
                                    length,
                                )
                            } else {
                                addLink(
                                    LinkAnnotation.Url(
                                        span.url,
                                        TextLinkStyles(
                                            SpanStyle(
                                                color = linkColor,
                                                textDecoration = TextDecoration.Underline,
                                            ),
                                        ),
                                    ),
                                    start,
                                    length,
                                )
                            }
                        }

                        is ChatInlineSpan.Fud -> {
                            val start = length
                            append(span.uri)
                            addStyle(
                                SpanStyle(color = mutedColor),
                                start,
                                length,
                            )
                        }
                    }
                }
            }
        }
    val rowModifier =
        if (isEncryptedThread) {
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isOwn) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ).padding(horizontal = 8.dp, vertical = 6.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongPress,
                )
        } else {
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongPress,
                )
        }
    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = annotated,
                style = MaterialTheme.typography.bodyMedium,
                color = bodyColor,
            )
            if (ChatMessageLexer.hasFud(line.text)) {
                ChatMessageLexer.fudUris(line.text).forEach { uri ->
                    Text(
                        text = stringResource(R.string.ns_chat_fud_tap_to_queue),
                        style = MaterialTheme.typography.labelSmall,
                        color = mutedColor,
                        modifier = Modifier.clickable { onFudTap(uri) },
                    )
                }
            }
            ChatInvoices.find(line.text)?.let { invoice ->
                Column(
                    modifier =
                        Modifier
                            .padding(top = 6.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.ns_chat_payment_request),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    invoice.amount?.takeIf { it.isNotBlank() }?.let { amt ->
                        Text(
                            text = "$amt ${invoice.token ?: "DRK"}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        text = invoice.address,
                        style = MaterialTheme.typography.labelSmall,
                        color = mutedColor,
                        maxLines = 1,
                    )
                    invoice.memo?.takeIf { it.isNotBlank() }?.let { memo ->
                        Text(
                            text = memo,
                            style = MaterialTheme.typography.labelSmall,
                            color = mutedColor,
                            maxLines = 2,
                        )
                    }
                    Text(
                        text = stringResource(R.string.ns_chat_pay_invoice),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onPayInvoice(invoice.uri) },
                    )
                }
            }
            val receiptRevision by ChatReceiptStore.revision.collectAsStateWithLifecycle()
            val context = LocalContext.current
            val receipt =
                remember(line.text, receiptRevision) {
                    val parsed = ChatReceiptCodec.parse(line.text) ?: return@remember null
                    ChatReceiptStore.get(context, parsed.id) ?: parsed
                }
            receipt?.let { card ->
                Text(
                    text = stringResource(receiptPhaseLabel(card.phase)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = "${card.amount} → ${card.address}",
                    style = MaterialTheme.typography.labelSmall,
                    color = mutedColor,
                    maxLines = 1,
                )
                card.txId?.takeIf { it.isNotBlank() }?.let { tx ->
                    Text(
                        text = tx,
                        style = MaterialTheme.typography.labelSmall,
                        color = mutedColor,
                        maxLines = 1,
                    )
                }
            }
            ChatDeliveryLabel.from(line.eventId, line.isOutgoing)?.let { delivery ->
                Text(
                    text = stringResource(deliveryLabel(delivery)),
                    style = MaterialTheme.typography.labelSmall,
                    color = mutedColor,
                    modifier =
                        Modifier.clickable(enabled = delivery == ChatDeliveryState.Failed) {
                            onRetry()
                        },
                )
            }
        }
        Text(
            text = time,
            style = MaterialTheme.typography.labelSmall,
            color = mutedColor,
        )
    }
}

@Composable
private fun CompactConnectionStatusBlock(
    nodeStatus: EmbeddedDarkircNodeStatus,
    connectionState: DarkfiChatConnectionState,
    diagnostic: String?,
    nickname: String,
    showRetry: Boolean,
    onRetry: () -> Unit,
    onToggleHud: () -> Unit,
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
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleHud),
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
        DarkfiChatConnectionState.ConnectedViaNym -> "IRC: connected (Nym)"
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
        when (prefs.privacyNetwork) {
            com.nighthawkapps.lib.android.sdk.net.PrivacyNetwork.Nym -> R.string.ns_chat_transport_nym
            com.nighthawkapps.lib.android.sdk.net.PrivacyNetwork.Tor -> R.string.ns_chat_transport_tor
            com.nighthawkapps.lib.android.sdk.net.PrivacyNetwork.Direct -> R.string.ns_chat_transport_direct
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
            DarkfiChatConnectionState.ConnectedViaNym -> R.string.ns_chat_state_connected_nym
            DarkfiChatConnectionState.Degraded -> R.string.ns_chat_state_degraded
            DarkfiChatConnectionState.Error -> R.string.ns_chat_state_error
        },
    )

internal fun shouldAutoScrollOnNewMessage(
    lastVisibleIndex: Int,
    totalItemsCount: Int,
): Boolean {
    if (totalItemsCount <= 0) return true
    return lastVisibleIndex >= (totalItemsCount - 3).coerceAtLeast(0)
}

internal fun formatTlsPinSummary(pinBytes: List<UByte>?): String? {
    val bytes = pinBytes?.takeIf { it.isNotEmpty() } ?: return null
    val hex = bytes.take(8).joinToString("") { "%02x".format(it.toInt()) }
    return "sha256:$hex..."
}
