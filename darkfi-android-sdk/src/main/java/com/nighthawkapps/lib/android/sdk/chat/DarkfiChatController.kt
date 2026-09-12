@file:Suppress("MaxLineLength", "TooManyFunctions")

package com.nighthawkapps.lib.android.sdk.chat

import android.content.Context
import androidx.work.WorkManager
import com.nighthawkapps.lib.android.sdk.chat.darkirc.DarkircContactCryptoConfig
import com.nighthawkapps.lib.android.sdk.chat.darkirc.DarkircCryptoManager
import com.nighthawkapps.lib.android.sdk.chat.darkirc.DarkircDaemonService
import com.nighthawkapps.lib.android.sdk.chat.dm.DmConversationLimits
import com.nighthawkapps.lib.android.sdk.chat.dm.DmConversationMeta
import com.nighthawkapps.lib.android.sdk.chat.dm.DmConversationStore
import com.nighthawkapps.lib.android.sdk.chat.hud.OutboundPeerSlot
import com.nighthawkapps.lib.android.sdk.chat.hud.OutboundPeerSlots
import com.nighthawkapps.lib.android.sdk.daemon.DarkfiChatStatusRegistry
import com.nighthawkapps.lib.android.spackle.Twig
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.DarkircEventCallback
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.darkircConnectionPhase
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.darkircStatus
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.sendChatMessage
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.startDarkirc
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.stopDarkirc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ChatChannelMessage(
    val eventId: String,
    val channel: String,
    val nick: String,
    val text: String,
    val timestampMs: Long,
)

/**
 * Bridges the Compose chat UI to a native EventGraph via FFI.
 *
 * Prefer [getOrCreate] so connect/read jobs survive Activity pause/recreate and
 * stay bound to an application-scoped [CoroutineScope] rather than `lifecycleScope`.
 */
class DarkfiChatController(
    private val scope: CoroutineScope,
    appContext: Context,
) {
    private val app = appContext.applicationContext
    private val preferences = DarkfiChatPreferences(app)
    private val outgoingQueue = DarkfiChatOutgoingQueueStore.create(app)

    /** Event IDs already delivered to the UI — prevents duplicate rendering from
     *  live relay + history replay or self-echo. */
    private val seenEventIds = LinkedHashSet<String>()

    private fun setConnectionState(state: DarkfiChatConnectionState) {
        _connectionState.value = state
        DarkfiChatStatusRegistry.update(state)
    }

    private val _connectionState = MutableStateFlow(DarkfiChatConnectionState.Disconnected)
    val connectionState: StateFlow<DarkfiChatConnectionState> = _connectionState.asStateFlow()

    private val _diagnosticDetail = MutableStateFlow<String?>(null)
    val diagnosticDetail: StateFlow<String?> = _diagnosticDetail.asStateFlow()

    private val _messagesByChannel = MutableStateFlow<Map<String, List<ChatChannelMessage>>>(emptyMap())
    val messagesByChannel: StateFlow<Map<String, List<ChatChannelMessage>>> = _messagesByChannel.asStateFlow()

    private val _joinedChannels = MutableStateFlow(DarkfiChatDefaults.DEFAULT_PUBLIC_CHANNELS.toMutableList())
    val joinedChannels: StateFlow<List<String>> = _joinedChannels.asStateFlow()

    private val _selectedChannel = MutableStateFlow(DarkfiChatDefaults.DEFAULT_PUBLIC_CHANNELS.firstOrNull() ?: "#dev")
    val selectedChannel: StateFlow<String> = _selectedChannel.asStateFlow()

    private val _dmContacts = MutableStateFlow<List<DarkircContactCryptoConfig>>(emptyList())
    val dmContacts: StateFlow<List<DarkircContactCryptoConfig>> = _dmContacts.asStateFlow()

    private val _dmConversations = MutableStateFlow<List<DmConversationMeta>>(emptyList())
    val dmConversations: StateFlow<List<DmConversationMeta>> = _dmConversations.asStateFlow()

    private val _embeddedNodeStatus = MutableStateFlow(EmbeddedDarkircNodeStatus.NotUsed)
    val embeddedNodeStatus: StateFlow<EmbeddedDarkircNodeStatus> = _embeddedNodeStatus.asStateFlow()

    private val _useTorForChat = MutableStateFlow(preferences.useTorForChat)
    val useTorForChat: StateFlow<Boolean> = _useTorForChat.asStateFlow()

    private val _outboundSlots =
        MutableStateFlow(OutboundPeerSlots.synthesize(phase = "stopped", daemonRunning = false))
    val outboundSlots: StateFlow<List<OutboundPeerSlot>> = _outboundSlots.asStateFlow()

    private var connectJob: Job? = null
    private var readJob: Job? = null

    val defaultChannels: List<String> get() = DarkfiChatDefaults.DEFAULT_PUBLIC_CHANNELS

    init {
        refreshDmStateFromDisk()
    }

    fun refreshDmStateFromDisk() {
        _dmContacts.value = DarkircCryptoManager.loadContacts(app)
        _dmConversations.value = DmConversationStore.loadAll(app)
    }

    var ircNickname: String
        get() = preferences.ircNickname
        set(value) {
            preferences.ircNickname = DarkfiChatPreferences.sanitizeNickname(value)
        }

    fun connectOrRetry() {
        if (connectJob?.isActive == true) {
            return
        }
        // Daemon already up: keep the existing EventGraph subscription, but
        // always refresh UI connection state (previously we returned while the
        // Compose screen still showed Disconnected after process reuse).
        val status = darkircStatus()
        if (status == "running" || status == "starting") {
            val useTor = preferences.useTorForChat
            val connectedState =
                if (useTor) DarkfiChatConnectionState.ConnectedViaTor else DarkfiChatConnectionState.ConnectedDirect
            if (preferences.runEmbeddedDarkirc) {
                DarkircDaemonService.start(app)
            }
            applyDarkircPhaseToUi(connectedState, useTor)
            if (readJob?.isActive != true) {
                readJob =
                    scope.launch(Dispatchers.IO) {
                        while (isActive) {
                            when (darkircStatus()) {
                                "failed" -> {
                                    setConnectionState(DarkfiChatConnectionState.Error)
                                    _embeddedNodeStatus.value = EmbeddedDarkircNodeStatus.Failed
                                    break
                                }

                                "running", "starting" -> {
                                    applyDarkircPhaseToUi(connectedState, useTor)
                                }

                                "stopping", "not_running" -> {
                                    setConnectionState(DarkfiChatConnectionState.Disconnected)
                                    _embeddedNodeStatus.value = EmbeddedDarkircNodeStatus.NotUsed
                                    break
                                }
                            }
                            delay(2000)
                        }
                    }
            }
            return
        }

        // If daemon is in a failed or stuck state, fully stop and drain before
        // restarting so the next start_darkirc is clean.
        if (status == "failed" || status == "stopping") {
            try {
                stopDarkirc()
            } catch (_: Exception) {
            }
        }

        connectJob =
            scope.launch(Dispatchers.IO) {
                runNativeDarkircSequence()
            }
    }

    private suspend fun runNativeDarkircSequence() {
        setConnectionState(DarkfiChatConnectionState.Connecting)
        _diagnosticDetail.value = "Starting native EventGraph daemon..."

        try {
            // If a previous daemon is still winding down, wait for it to fully
            // stop before starting a new one, otherwise the native start is
            // rejected while the status is still "stopping".
            awaitDaemonNotRunning()

            val root = File(app.filesDir, "darkirc")
            root.mkdirs()
            val sledDb = File(root, "darkirc_db")
            sledDb.mkdirs()

            // Resolve the Tor transport. When the user enabled Tor for chat we
            // route the native EventGraph P2P through the same SOCKS5 proxy the
            // wallet uses (Guardian tor-android when embedded, else an external
            // SOCKS). We must have a reachable SOCKS port before starting the
            // daemon; otherwise we fail loudly rather than silently leaking
            // over clearnet.
            val useTor = preferences.useTorForChat
            val torSocksPort: Int? = if (useTor) resolveTorSocksPortOrNull() else null
            if (useTor && torSocksPort == null) {
                _embeddedNodeStatus.value = EmbeddedDarkircNodeStatus.Failed
                setConnectionState(DarkfiChatConnectionState.Error)
                _diagnosticDetail.value = "Tor is enabled for chat but no SOCKS proxy is reachable"
                return
            }

            val callback =
                object : DarkircEventCallback {
                    override fun onMessage(
                        eventId: String,
                        channel: String,
                        nick: String,
                        message: String,
                        timestamp: ULong
                    ) {
                        val rawMsg =
                            ChatChannelMessage(
                                eventId = eventId,
                                channel = channel,
                                nick = nick,
                                text = message,
                                timestampMs = timestamp.toLong()
                            )
                        ingestIncomingMessage(rawMsg)
                    }
                }

            startDarkirc(
                datastorePath = sledDb.absolutePath,
                useTor = useTor,
                torSocksPort = (torSocksPort ?: 0).toUShort(),
                callback = callback,
            )
            // Raise FGS while chat is active so Android does not suspend P2P sockets.
            if (preferences.runEmbeddedDarkirc) {
                DarkircDaemonService.start(app)
            }
            _embeddedNodeStatus.value = EmbeddedDarkircNodeStatus.Starting

            // The connected posture must reflect the transport actually in use
            // so the UI's Tor indicator is honest.
            val connectedState =
                if (useTor) DarkfiChatConnectionState.ConnectedViaTor else DarkfiChatConnectionState.ConnectedDirect

            // Poll fine-grained phase so UI stays on Connecting until peers + DAG sync.
            readJob =
                scope.launch(Dispatchers.IO) {
                    while (isActive) {
                        when (darkircStatus()) {
                            "failed" -> {
                                setConnectionState(DarkfiChatConnectionState.Error)
                                _embeddedNodeStatus.value = EmbeddedDarkircNodeStatus.Failed
                                break
                            }

                            "running", "starting" -> {
                                val wasConnected =
                                    _connectionState.value == DarkfiChatConnectionState.ConnectedDirect ||
                                        _connectionState.value == DarkfiChatConnectionState.ConnectedViaTor
                                applyDarkircPhaseToUi(connectedState, useTor)
                                val nowConnected =
                                    _connectionState.value == DarkfiChatConnectionState.ConnectedDirect ||
                                        _connectionState.value == DarkfiChatConnectionState.ConnectedViaTor
                                if (!wasConnected && nowConnected) {
                                    drainOutgoingQueue()
                                }
                            }

                            "stopping", "not_running" -> {
                                setConnectionState(DarkfiChatConnectionState.Disconnected)
                                _embeddedNodeStatus.value = EmbeddedDarkircNodeStatus.NotUsed
                                break
                            }
                        }
                        delay(2000)
                    }
                }
        } catch (e: Exception) {
            Twig.error(e) { "DarkIRC connect failed via FFI" }
            _embeddedNodeStatus.value = EmbeddedDarkircNodeStatus.Failed
            setConnectionState(DarkfiChatConnectionState.Error)
            _diagnosticDetail.value = e.message
        }
    }

    /**
     * Resolve a reachable Tor SOCKS5 port for the chat daemon, or null if none
     * is available. Uses the same [AppTorCoordinator] path as wallet routing so
     * we wait for Arti to finish bootstrapping (not only TCP bind).
     */
    private suspend fun resolveTorSocksPortOrNull(): Int? {
        _diagnosticDetail.value = "Waiting for Tor SOCKS proxy..."
        return com.nighthawkapps.lib.android.sdk.tor.AppTorCoordinator
            .ensureSocksReady(app)
    }

    /**
     * Map native `darkirc_connection_phase` onto UI connection state.
     * Only reports Connected when phase is `connected` — earlier phases stay Connecting.
     */
    private fun applyDarkircPhaseToUi(
        connectedState: DarkfiChatConnectionState,
        useTor: Boolean,
    ) {
        val phase =
            runCatching { darkircConnectionPhase() }.getOrDefault("starting")
        _diagnosticDetail.value =
            when (phase) {
                "connected" -> {
                    if (useTor) {
                        "Native FFI EventGraph Connected (Tor)"
                    } else {
                        "Native FFI EventGraph Connected"
                    }
                }

                "waiting_for_peers" -> {
                    "Waiting for darkirc peers..."
                }

                "static_sync" -> {
                    "Syncing darkirc static DAG..."
                }

                "syncing_dag" -> {
                    "Syncing darkirc message history..."
                }

                "loading_history" -> {
                    "Loading darkirc history..."
                }

                "failed" -> {
                    "DarkIRC failed"
                }

                "stopping" -> {
                    "Stopping DarkIRC..."
                }

                "stopped" -> {
                    "DarkIRC stopped"
                }

                else -> {
                    "Starting DarkIRC ($phase)..."
                }
            }
        when (phase) {
            "connected" -> {
                setConnectionState(connectedState)
                _embeddedNodeStatus.value = EmbeddedDarkircNodeStatus.Running
            }

            "failed" -> {
                setConnectionState(DarkfiChatConnectionState.Error)
                _embeddedNodeStatus.value = EmbeddedDarkircNodeStatus.Failed
            }

            "stopped", "stopping" -> {
                setConnectionState(DarkfiChatConnectionState.Disconnected)
                _embeddedNodeStatus.value = EmbeddedDarkircNodeStatus.NotUsed
            }

            else -> {
                setConnectionState(DarkfiChatConnectionState.Connecting)
                _embeddedNodeStatus.value = EmbeddedDarkircNodeStatus.Starting
            }
        }
        refreshOutboundSlots(phase)
    }

    private fun darkircDatastoreDir(): File = File(File(app.filesDir, "darkirc"), "darkirc_db")

    fun refreshOutboundSlots(phase: String? = null) {
        val resolvedPhase =
            phase ?: runCatching { darkircConnectionPhase() }.getOrDefault("stopped")
        val running =
            runCatching { darkircStatus() }.getOrDefault("not_running") in
                setOf("running", "starting")
        val json = OutboundPeerSlots.readFile(darkircDatastoreDir())
        _outboundSlots.value = OutboundPeerSlots.resolve(json, resolvedPhase, running)
    }

    /**
     * Persist tcp/tor for chat and restart the EventGraph so the HUD switch is real.
     * SOCKS still comes from Settings → Tor.
     */
    fun applyChatTransport(useTor: Boolean) {
        if (preferences.useTorForChat == useTor && darkircStatus() in setOf("running", "starting")) {
            _useTorForChat.value = useTor
            return
        }
        preferences.useTorForChat = useTor
        _useTorForChat.value = useTor
        scope.launch(Dispatchers.IO) {
            stopAndAwait()
            connectOrRetry()
        }
    }

    private suspend fun awaitDaemonNotRunning(timeoutMs: Long = 5_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            when (darkircStatus()) {
                "not_running", "failed" -> return

                // If the daemon is stuck in 'starting' for the full timeout,
                // break out so the caller can decide to force-proceed.
                else -> delay(200)
            }
        }
        Twig.warn { "awaitDaemonNotRunning timed out (status=${darkircStatus()})" }
    }

    fun stop() {
        connectJob?.cancel()
        connectJob = null
        readJob?.cancel()
        readJob = null
        setConnectionState(DarkfiChatConnectionState.Disconnected)
        _diagnosticDetail.value = null
        _embeddedNodeStatus.value = EmbeddedDarkircNodeStatus.NotUsed
        try {
            stopDarkirc()
        } catch (e: Exception) {
            Twig.error(e) { "Failed to stop darkirc daemon" }
        }
        DarkircDaemonService.stop(app)
    }

    /**
     * Stop the daemon and wait for it to reach `not_running` / `failed` before
     * returning. Use this when you need to immediately restart — plain [stop]
     * is fire-and-forget, which can leave the daemon in `stopping` when
     * [connectOrRetry] runs.
     */
    suspend fun stopAndAwait(timeoutMs: Long = 5_000) {
        stop()
        awaitDaemonNotRunning(timeoutMs)
    }

    private fun ingestIncomingMessage(rawMsg: ChatChannelMessage) {
        // Deduplicate: skip if this event was already delivered
        synchronized(seenEventIds) {
            if (!seenEventIds.add(rawMsg.eventId)) return
            // Prune to cap memory — oldest entries are evicted when set grows
            // beyond 10k. LinkedHashSet preserves insertion order.
            if (seenEventIds.size > SEEN_EVENT_IDS_MAX) {
                val it = seenEventIds.iterator()
                while (seenEventIds.size > SEEN_EVENT_IDS_MAX - 1000 && it.hasNext()) {
                    it.next()
                    it.remove()
                }
            }
        }
        val msg =
            DarkfiChatCrypto.decryptMessageIfPossible(
                app,
                rawMsg,
                delegateWireCryptoToDaemon = false,
            )
        _messagesByChannel.update { cur ->
            val next = cur.toMutableMap()
            val key = msg.channel
            val existing = next[key].orEmpty()
            // Drop optimistic local echo once the real EventGraph id arrives
            // (same nick + text on the same channel within the last minute).
            val pruned =
                existing.filterNot { line ->
                    line.eventId.startsWith("local-") &&
                        line.nick == msg.nick &&
                        line.text == msg.text &&
                        (msg.timestampMs - line.timestampMs) in -60_000..60_000
                }
            next[key] = pruned + msg
            next
        }
        if (!msg.channel.startsWith("#")) {
            DmConversationStore.touchMessage(
                app,
                msg.channel,
                preview = "${msg.nick}: ${msg.text}",
                atMs = msg.timestampMs,
            )
            refreshDmStateFromDisk()
        }
    }

    private fun recordLocalOutgoing(
        contactLabel: String,
        plaintext: String,
    ) {
        val nick = preferences.ensureIrcNickname()
        val line =
            ChatChannelMessage(
                eventId = "local-${System.nanoTime()}",
                channel = contactLabel,
                nick = nick,
                text = plaintext,
                timestampMs = System.currentTimeMillis(),
            )
        _messagesByChannel.update { cur ->
            val next = cur.toMutableMap()
            next[contactLabel] = next[contactLabel].orEmpty() + line
            next
        }
        if (!contactLabel.startsWith("#")) {
            DmConversationStore.touchMessage(
                app,
                contactLabel,
                preview = "$nick: $plaintext",
                atMs = line.timestampMs,
            )
            refreshDmStateFromDisk()
        }
    }

    private fun drainOutgoingQueue() {
        val pending = outgoingQueue.loadAll()
        if (pending.isEmpty()) return
        for (rec in pending) {
            try {
                val body =
                    DarkfiChatCrypto.encryptMessageIfPossible(
                        app,
                        rec.channelNormalized,
                        rec.textSanitized,
                        delegateWireCryptoToDaemon = false
                    )
                val nick = preferences.ensureIrcNickname()
                sendChatMessage(rec.channelNormalized, nick, body)
                outgoingQueue.remove(rec.id)
            } catch (e: Exception) {
                Twig.warn(e) { "Stopping drain at ${rec.id}" }
                break
            }
        }
        if (outgoingQueue.loadAll().isEmpty()) {
            WorkManager.getInstance(app).cancelUniqueWork(OutgoingChatReconnectWorker.UNIQUE_WORK_NAME)
        }
    }

    private fun recordSystemMessage(
        contactLabel: String,
        plaintext: String,
    ) {
        val line =
            ChatChannelMessage(
                eventId = "system-${System.nanoTime()}",
                channel = contactLabel,
                nick = "System",
                text = plaintext,
                timestampMs = System.currentTimeMillis(),
            )
        _messagesByChannel.update { cur ->
            val next = cur.toMutableMap()
            next[contactLabel] = next[contactLabel].orEmpty() + line
            next
        }
    }

    fun sendToChannel(
        channel: String,
        text: String
    ) {
        val normalized = if (channel.startsWith("#")) channel else channel
        val body = text.trim().takeIf { it.isNotEmpty() } ?: return

        // Handle all DarkIRC slash commands as per DarkFi manual
        if (body.startsWith("/")) {
            val parts = body.split("\\s+".toRegex(), limit = 2)
            val cmd = parts.firstOrNull()?.lowercase() ?: ""
            val arg = parts.getOrNull(1)?.trim() ?: ""

            when (cmd) {
                "/nick" -> {
                    if (arg.isNotEmpty() && arg.length <= 24) {
                        val sanitized = DarkfiChatPreferences.sanitizeNickname(arg)
                        preferences.ircNickname = sanitized
                        recordSystemMessage(normalized, "Your nickname is now: $sanitized")
                        return
                    }
                    recordSystemMessage(normalized, "Invalid nickname. Usage: /nick <name> (1–24 alphanumeric/underscore characters)")
                    return
                }

                "/join" -> {
                    if (arg.isNotEmpty()) {
                        val targetChan = if (arg.startsWith("#")) arg else "#$arg"
                        _joinedChannels.update { cur ->
                            if (targetChan !in cur) (cur + targetChan).toMutableList() else cur
                        }
                        _selectedChannel.value = targetChan
                        recordSystemMessage(targetChan, "Joined channel: $targetChan")
                        return
                    }
                    recordSystemMessage(normalized, "Usage: /join <#channel>")
                    return
                }

                "/part", "/leave" -> {
                    _joinedChannels.update { cur ->
                        cur.toMutableList().also { it.remove(normalized) }
                    }
                    _messagesByChannel.update { cur ->
                        val next = cur.toMutableMap()
                        next.remove(normalized)
                        next
                    }
                    val remaining = _joinedChannels.value
                    if (remaining.isNotEmpty()) {
                        _selectedChannel.value = remaining.first()
                    }
                    recordSystemMessage(
                        remaining.firstOrNull() ?: normalized,
                        "Left channel: $normalized"
                    )
                    return
                }

                "/clear" -> {
                    _messagesByChannel.update { cur ->
                        val next = cur.toMutableMap()
                        next[normalized] = emptyList()
                        next
                    }
                    return
                }

                "/me" -> {
                    if (arg.isNotEmpty()) {
                        val nick = preferences.ensureIrcNickname()
                        val actionText = "* $nick $arg"
                        recordLocalOutgoing(normalized, actionText)
                        scope.launch(Dispatchers.IO) {
                            try {
                                sendChatMessage(normalized, nick, actionText)
                            } catch (e: Exception) {
                                Twig.error(e) { "FFI send failed" }
                            }
                        }
                        return
                    }
                    recordSystemMessage(normalized, "Usage: /me <action>")
                    return
                }

                "/msg" -> {
                    val msgParts = arg.split("\\s+".toRegex(), limit = 2)
                    if (msgParts.size == 2) {
                        val target = msgParts[0].trim()
                        val msgContent = msgParts[1].trim()
                        val nick = preferences.ensureIrcNickname()
                        scope.launch(Dispatchers.IO) {
                            try {
                                sendChatMessage(target, nick, msgContent)
                            } catch (e: Exception) {
                                Twig.error(e) { "FFI send /msg failed" }
                            }
                        }
                        return
                    }
                    recordSystemMessage(normalized, "Usage: /msg <target> <message>")
                    return
                }

                "/help" -> {
                    val helpText =
                        """
                        Available DarkIRC commands:
                          /nick <name> — Change nickname (1–24 characters)
                          /join <#channel> — Join or switch to channel
                          /part — Leave current channel
                          /clear — Clear messages in current view
                          /me <action> — Send action message (* nick action)
                          /msg <target> <text> — Send message to target
                          /help — Show this help message
                        """.trimIndent()
                    recordSystemMessage(normalized, helpText)
                    return
                }

                else -> {
                    recordSystemMessage(normalized, "Unknown command '$cmd'. Type /help for DarkIRC commands.")
                    return
                }
            }
        }

        val wireBody =
            DarkfiChatCrypto.encryptMessageIfPossible(
                app,
                normalized,
                body,
                delegateWireCryptoToDaemon = false
            )

        if (darkircStatus() == "running") {
            // Optimistic local bubble so public-channel sends are never invisible
            // while waiting for EventGraph self-echo / peer relay.
            recordLocalOutgoing(normalized, body)
            scope.launch(Dispatchers.IO) {
                try {
                    val nick = preferences.ensureIrcNickname()
                    sendChatMessage(normalized, nick, wireBody)
                } catch (e: Exception) {
                    Twig.error(e) { "FFI send failed" }
                    _diagnosticDetail.value = "Send failed: ${e.message ?: e.javaClass.simpleName}"
                }
            }
            return
        }

        outgoingQueue.enqueue(
            PendingOutgoingChatRecord(
                channelNormalized = normalized,
                textSanitized = body,
                queuedAtEpochMs = System.currentTimeMillis(),
            ),
        )
        OutgoingChatReconnectWorker.schedule(app)

        scope.launch(Dispatchers.IO) {
            val nick = preferences.ensureIrcNickname()
            val line =
                ChatChannelMessage(
                    eventId = "queued-${System.nanoTime()}",
                    channel = normalized,
                    nick = nick + " (queued)",
                    text = body,
                    timestampMs = System.currentTimeMillis(),
                )
            _messagesByChannel.update { cur ->
                val next = cur.toMutableMap()
                next[normalized] = next[normalized].orEmpty() + line
                next
            }
        }
    }

    suspend fun addDmContact(
        contactLabel: String,
        theirPublicBase58: String,
        generateMySecret: Boolean = true,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val label = contactLabel.trim().take(DmConversationLimits.CONTACT_LABEL_MAX_LEN)
                require(label.isNotBlank()) { "contact label required" }
                val theirPublic = theirPublicBase58.trim()
                val mySecret =
                    if (generateMySecret) {
                        DarkircCryptoManager.generateDmKeypair(app)?.myDmChachaSecretBase58
                            ?: error("could not generate DM secret")
                    } else {
                        error("my secret required")
                    }
                val config =
                    DarkircContactCryptoConfig(
                        nick = label,
                        dmChachaPublicBase58 = theirPublic,
                        myDmChachaSecretBase58 = mySecret,
                    )
                DarkircCryptoManager.saveContact(app, config)
                DmConversationStore.upsert(
                    app,
                    DmConversationMeta(
                        contactLabel = label,
                        peerPublicKeyBase58 = theirPublic,
                    ),
                )
                refreshDmStateFromDisk()
            }
        }

    suspend fun removeDmContact(contactLabel: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val label = contactLabel.trim()
                DarkircCryptoManager.removeContact(app, label)
                DmConversationStore.remove(app, label)
                _messagesByChannel.update { cur -> cur - label }
                refreshDmStateFromDisk()
            }
        }

    fun generateEphemeralDmShareKeypair() = DarkircCryptoManager.generateDmKeypair(app)

    companion object {
        private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        @Volatile
        private var instance: DarkfiChatController? = null

        /** Process-wide controller; safe to call from UI and daemon lifecycle. */
        fun getOrCreate(appContext: Context): DarkfiChatController =
            instance ?: synchronized(this) {
                instance ?: DarkfiChatController(appScope, appContext.applicationContext).also {
                    instance = it
                }
            }

        /** Cap for the seenEventIds dedup set. Oldest entries are pruned when
         *  the set exceeds this size to prevent unbounded memory growth. */
        private const val SEEN_EVENT_IDS_MAX = 10_000
    }
}
