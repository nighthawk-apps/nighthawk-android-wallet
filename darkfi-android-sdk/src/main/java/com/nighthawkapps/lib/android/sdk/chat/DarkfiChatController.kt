@file:Suppress("MaxLineLength", "TooManyFunctions")

package com.nighthawkapps.lib.android.sdk.chat

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
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
import com.nighthawkapps.lib.android.sdk.net.PrivacyNetwork
import com.nighthawkapps.lib.android.sdk.net.SessionTransport
import com.nighthawkapps.lib.android.sdk.nym.AppNymCoordinator
import com.nighthawkapps.lib.android.spackle.Twig
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.DarkircEventCallback
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.DarkircTransport
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.configureDarkirc
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.darkircConnectionPhase
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.darkircOutboundSlots
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
    val isOutgoing: Boolean = false,
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

    private val _selectedChannel =
        MutableStateFlow(DarkfiChatDefaults.DEFAULT_PUBLIC_CHANNELS.firstOrNull() ?: "#nighthawk")
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

    private val _chatIdentityPublicId = MutableStateFlow<String?>(null)
    val chatIdentityPublicId: StateFlow<String?> = _chatIdentityPublicId.asStateFlow()

    private var connectJob: Job? = null
    private var readJob: Job? = null

    /** Transport the running daemon was started with, so the connected state reports it honestly. */
    @Volatile
    private var activeTransport: DarkircTransport? = null

    val defaultChannels: List<String> get() = DarkfiChatDefaults.DEFAULT_PUBLIC_CHANNELS

    init {
        scope.launch(Dispatchers.IO) {
            DarkircCryptoManager.scrubLegacyBundleFiles(app)
            refreshDmStateFromDisk()
            refreshChatIdentity(DarkfiChatIdentity(app))
            val loaded = ChatHistoryStore.load(app)
            if (loaded.isEmpty()) return@launch
            _messagesByChannel.update { cur -> if (cur.isEmpty()) loaded else cur }
        }
    }

    fun refreshDmStateFromDisk() {
        _dmContacts.value = DarkircCryptoManager.loadContacts(app)
        _dmConversations.value = DmConversationStore.loadAll(app)
    }

    fun refreshChatIdentity(identity: DarkfiChatIdentity = DarkfiChatIdentity(app)) {
        val pubHex = runCatching { identity.publicIdHex() }.getOrNull()?.takeIf { it.isNotBlank() }
        _chatIdentityPublicId.value = pubHex
        if (pubHex != null && preferences.ircNickname.isBlank()) {
            preferences.ircNickname =
                DarkfiChatPreferences.sanitizeNickname("hawk_${pubHex.take(IDENTITY_NICK_PREFIX_LEN)}")
        }
    }

    fun onAppResume() {
        scope.launch(Dispatchers.IO) {
            refreshOutboundSlots()
            if (isReadyForImmediateSend(
                    runCatching { darkircStatus() }.getOrDefault("not_running"),
                    runCatching { darkircConnectionPhase() }.getOrDefault("stopped"),
                )
            ) {
                drainOutgoingQueue()
            }
        }
    }

    private fun isAppInForeground(): Boolean =
        runCatching {
            ProcessLifecycleOwner
                .get()
                .lifecycle.currentState
                .isAtLeast(Lifecycle.State.STARTED)
        }.getOrDefault(false)

    var ircNickname: String
        get() = preferences.ircNickname
        set(value) {
            preferences.ircNickname = DarkfiChatPreferences.sanitizeNickname(value)
        }

    fun connectOrRetry() {
        val disabled = !preferences.runEmbeddedDarkirc
        if (disabled) stop()
        if (disabled || connectJob?.isActive == true) return
        // Daemon already up: keep the existing EventGraph subscription, but
        // always refresh UI connection state (previously we returned while the
        // Compose screen still showed Disconnected after process reuse).
        val status = darkircStatus()
        if (status == "running" || status == "starting") {
            val transport = activeTransport ?: chatTransportFor(SessionTransport.effective(preferences))
            val connectedState = connectedStateFor(transport)
            if (preferences.runEmbeddedDarkirc && isAppInForeground()) {
                DarkircDaemonService.start(app)
            }
            if (readJob?.isActive != true) {
                readJob =
                    scope.launch(Dispatchers.IO) {
                        applyDarkircPhaseToUi(connectedState, transport)
                        while (isActive) {
                            when (darkircStatus()) {
                                "failed" -> {
                                    setConnectionState(DarkfiChatConnectionState.Error)
                                    _embeddedNodeStatus.value = EmbeddedDarkircNodeStatus.Failed
                                    break
                                }

                                "running", "starting" -> {
                                    val wasConnected = _connectionState.value.isConnected
                                    applyDarkircPhaseToUi(connectedState, transport)
                                    val nowConnected = _connectionState.value.isConnected
                                    if (nowConnected && (!wasConnected || outgoingQueue.loadAll().any { it.delivery == "queued" })) {
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
            } else {
                scope.launch(Dispatchers.IO) {
                    applyDarkircPhaseToUi(connectedState, transport)
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

            // Chat P2P follows the app network. Nym and Tor need their SOCKS proxy carrying
            // traffic before the daemon starts; otherwise fail loudly rather than silently
            // leaking over clearnet.
            val transport = chatTransportFor(SessionTransport.effective(preferences))
            val socksPort: Int? =
                when (transport) {
                    DarkircTransport.NYM -> resolveNymSocksPortOrNull()
                    DarkircTransport.TOR -> resolveTorSocksPortOrNull()
                    DarkircTransport.DIRECT -> 0
                }
            if (socksPort == null) {
                _embeddedNodeStatus.value = EmbeddedDarkircNodeStatus.Failed
                setConnectionState(DarkfiChatConnectionState.Error)
                _diagnosticDetail.value =
                    if (transport == DarkircTransport.NYM) {
                        "Nym is the app network but the mixnet tunnel is not up"
                    } else {
                        "Tor is the app network but no SOCKS proxy is reachable"
                    }
                return
            }

            val callback =
                object : DarkircEventCallback {
                    override fun onMessage(
                        eventId: String,
                        channel: String,
                        nick: String,
                        message: String,
                        timestamp: ULong,
                        isOutgoing: Boolean,
                    ) {
                        val rawMsg =
                            ChatChannelMessage(
                                eventId = eventId,
                                channel = channel,
                                nick = nick,
                                text = message,
                                timestampMs = timestamp.toLong(),
                                isOutgoing = isOutgoing,
                            )
                        ingestIncomingMessage(rawMsg)
                    }
                }

            // Register DM contacts in native daemon memory without writing secrets to disk,
            // and apply persisted darkirc DAG / fast-mode configuration before starting.
            DarkircCryptoManager.syncContactsToNative(app)
            runCatching {
                configureDarkirc(
                    preferences.darkircDagsCount.toUInt(),
                    preferences.darkircFastMode,
                )
            }.onFailure { Twig.warn(it) { "configureDarkirc before start failed" } }
            startDarkirc(
                datastorePath = sledDb.absolutePath,
                transport = transport,
                socksPort = socksPort.toUShort(),
                callback = callback,
            )
            activeTransport = transport
            // Raise FGS while chat is active in foreground so Android does not suspend P2P sockets.
            if (preferences.runEmbeddedDarkirc && isAppInForeground()) {
                DarkircDaemonService.start(app)
            }
            _embeddedNodeStatus.value = EmbeddedDarkircNodeStatus.Starting

            // The connected posture must reflect the transport actually in use
            // so the UI's network indicator is honest.
            val connectedState = connectedStateFor(transport)

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
                                val wasConnected = _connectionState.value.isConnected
                                applyDarkircPhaseToUi(connectedState, transport)
                                val nowConnected = _connectionState.value.isConnected
                                if (nowConnected && (!wasConnected || outgoingQueue.loadAll().any { it.delivery == "queued" })) {
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
    private suspend fun resolveNymSocksPortOrNull(): Int? {
        _diagnosticDetail.value = "Waiting for the Nym mixnet..."
        return AppNymCoordinator.ensureReady(app)
    }

    private fun connectedStateFor(transport: DarkircTransport): DarkfiChatConnectionState =
        when (transport) {
            DarkircTransport.NYM -> DarkfiChatConnectionState.ConnectedViaNym
            DarkircTransport.TOR -> DarkfiChatConnectionState.ConnectedViaTor
            DarkircTransport.DIRECT -> DarkfiChatConnectionState.ConnectedDirect
        }

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
        transport: DarkircTransport,
    ) {
        val phase =
            runCatching { darkircConnectionPhase() }.getOrDefault("starting")
        _diagnosticDetail.value =
            when (phase) {
                "connected" -> {
                    when (transport) {
                        DarkircTransport.NYM -> "Native FFI EventGraph Connected (Nym)"
                        DarkircTransport.TOR -> "Native FFI EventGraph Connected (Tor)"
                        DarkircTransport.DIRECT -> "Native FFI EventGraph Connected (direct)"
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
        val nativeJson =
            runCatching { darkircOutboundSlots() }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
        val json = nativeJson ?: OutboundPeerSlots.readFile(darkircDatastoreDir())
        _outboundSlots.value = OutboundPeerSlots.resolve(json, resolvedPhase, running)
    }

    suspend fun restartDaemon(): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                configureDarkirc(
                    preferences.darkircDagsCount.toUInt(),
                    preferences.darkircFastMode,
                )
            }
            stopAndAwait()
            runNativeDarkircSequence()
            runCatching { darkircStatus() in setOf("running", "starting") }.getOrDefault(false)
        }

    suspend fun configureAndMaybeRestart(
        dagsCount: Int,
        fastMode: Boolean,
        restart: Boolean,
    ): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                configureDarkirc(dagsCount.toUInt(), fastMode)
            }.onFailure { Twig.warn(it) { "configureDarkirc failed" } }
            if (restart) {
                restartDaemon()
            } else {
                true
            }
        }

    suspend fun connectAndDrainHeadless(timeoutMs: Long = 60_000L): Boolean =
        withContext(Dispatchers.IO) {
            val pending = outgoingQueue.loadAll()
            if (pending.none { it.delivery != "failed" }) {
                if (pending.isEmpty()) {
                    WorkManager.getInstance(app).cancelUniqueWork(OutgoingChatReconnectWorker.UNIQUE_WORK_NAME)
                }
                return@withContext true
            }

            if (!isReadyForImmediateSend(
                    runCatching { darkircStatus() }.getOrDefault("not_running"),
                    runCatching { darkircConnectionPhase() }.getOrDefault("stopped"),
                )
            ) {
                connectOrRetry()
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    val st = runCatching { darkircStatus() }.getOrDefault("not_running")
                    val ph = runCatching { darkircConnectionPhase() }.getOrDefault("stopped")
                    if (isReadyForImmediateSend(st, ph)) break
                    if (st == "failed") break
                    delay(1_000L)
                }
            }

            val drained = drainOutgoingQueue()
            if (drained && !isAppInForeground()) {
                stop()
            }
            drained
        }

    /**
     * Persist Tor on/off as the app network and restart the EventGraph so the HUD switch is real.
     * SOCKS still comes from Settings → Network privacy.
     */
    fun applyChatTransport(useTor: Boolean) {
        preferences.useTorForChat = useTor
        restartIfNetworkChanged()
    }

    /**
     * Reconnect entry point: restarts the daemon when the app network no longer matches the
     * transport it runs on, otherwise behaves like [connectOrRetry].
     */
    fun restartIfNetworkChanged() {
        _useTorForChat.value = preferences.useTorForChat
        val wanted = chatTransportFor(SessionTransport.effective(preferences))
        val running = darkircStatus() in setOf("running", "starting")
        if (!running || activeTransport == null || activeTransport == wanted) {
            connectOrRetry()
            return
        }
        Twig.info { "DarkIRC: app network changed ($activeTransport -> $wanted), restarting daemon" }
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
        Twig.warn {
            "awaitDaemonNotRunning timed out (status=${darkircStatus()}). " +
                "A start after a stop has been pending replaces a stuck daemon."
        }
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
                delegateWireCryptoToDaemon = true,
            )
        _messagesByChannel.update { cur ->
            val next = cur.toMutableMap()
            val key = msg.channel
            val existing = next[key].orEmpty()
            // Drop optimistic local/queued echo once the real EventGraph id arrives.
            val pruned = pruneOptimisticMatch(existing, msg)
            next[key] = pruned + msg
            next
        }
        ChatHistoryStore.append(app, msg)
        ChatMessageNotifier.maybeNotify(
            context = app,
            channel = msg.channel,
            nick = msg.nick,
            preview = msg.text,
            isOutgoing = msg.isOutgoing,
            timestampMs = msg.timestampMs,
            ownNick = preferences.ensureIrcNickname(),
        )
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
    ): String {
        val nick = preferences.ensureIrcNickname()
        val eventId = "local-${System.nanoTime()}"
        val line =
            ChatChannelMessage(
                eventId = eventId,
                channel = contactLabel,
                nick = nick,
                text = plaintext,
                timestampMs = System.currentTimeMillis(),
                isOutgoing = true,
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
        ChatHistoryStore.append(app, line)
        return eventId
    }

    private fun retagOutgoing(
        channel: String,
        eventId: String,
        prefix: String,
    ) {
        _messagesByChannel.update { cur ->
            val next = cur.toMutableMap()
            next[channel] =
                next[channel].orEmpty().map { line ->
                    if (line.eventId == eventId) {
                        line.copy(eventId = "$prefix-${line.eventId.substringAfter('-', line.eventId)}")
                    } else {
                        line
                    }
                }
            next
        }
    }

    private fun retagFirstQueuedOutgoing(
        channel: String,
        text: String,
        prefix: String,
    ) {
        _messagesByChannel.update { cur ->
            val next = cur.toMutableMap()
            var retagged = false
            next[channel] =
                next[channel].orEmpty().map { line ->
                    if (!retagged && line.eventId.startsWith("queued-") && line.text == text) {
                        retagged = true
                        line.copy(eventId = "$prefix-${line.eventId.substringAfter('-', line.eventId)}")
                    } else {
                        line
                    }
                }
            next
        }
    }

    private fun drainOutgoingQueue(): Boolean {
        val pending = outgoingQueue.loadAll().filter { it.delivery != "failed" }
        val status = runCatching { darkircStatus() }.getOrDefault("not_running")
        val phase = runCatching { darkircConnectionPhase() }.getOrDefault("stopped")
        if (pending.isNotEmpty() && isReadyForImmediateSend(status, phase)) {
            DarkircCryptoManager.syncContactsToNative(app)
            for (rec in pending) {
                if (!trySendQueuedRecord(rec)) break
            }
        }
        val remaining = outgoingQueue.loadAll()
        if (remaining.isEmpty()) {
            WorkManager.getInstance(app).cancelUniqueWork(OutgoingChatReconnectWorker.UNIQUE_WORK_NAME)
        }
        return remaining.none { it.delivery != "failed" }
    }

    private fun trySendQueuedRecord(rec: PendingOutgoingChatRecord): Boolean {
        val currentStatus = runCatching { darkircStatus() }.getOrDefault("not_running")
        val currentPhase = runCatching { darkircConnectionPhase() }.getOrDefault("stopped")
        if (!isReadyForImmediateSend(currentStatus, currentPhase)) {
            return false
        }
        outgoingQueue.updateDelivery(rec.id, "sending")
        return try {
            val body =
                DarkfiChatCrypto.encryptMessageIfPossible(
                    app,
                    rec.channelNormalized,
                    rec.textSanitized,
                    delegateWireCryptoToDaemon = true,
                )
            val nick = preferences.ensureIrcNickname()
            sendChatMessage(rec.channelNormalized, nick, body)
            outgoingQueue.remove(rec.id)
            retagFirstQueuedOutgoing(rec.channelNormalized, rec.textSanitized, "local")
            true
        } catch (e: Exception) {
            val retryable =
                !isReadyForImmediateSend(
                    runCatching { darkircStatus() }.getOrDefault("not_running"),
                    runCatching { darkircConnectionPhase() }.getOrDefault("stopped"),
                )
            outgoingQueue.updateDelivery(rec.id, if (retryable) "queued" else "failed")
            Twig.warn(e) { "Stopping drain at ${rec.id}" }
            false
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
                        val localId = recordLocalOutgoing(normalized, actionText)
                        scope.launch(Dispatchers.IO) {
                            try {
                                DarkircCryptoManager.syncContactsToNative(app)
                                sendChatMessage(normalized, nick, actionText)
                            } catch (e: Exception) {
                                retagOutgoing(normalized, localId, "failed")
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
                        scope.launch(Dispatchers.IO) {
                            try {
                                val nick = preferences.ensureIrcNickname()
                                val wireBody =
                                    DarkfiChatCrypto.encryptMessageIfPossible(
                                        app,
                                        target,
                                        msgContent,
                                        delegateWireCryptoToDaemon = true,
                                    )
                                DarkircCryptoManager.syncContactsToNative(app)
                                sendChatMessage(target, nick, wireBody)
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

        scope.launch(Dispatchers.IO) {
            val status = runCatching { darkircStatus() }.getOrDefault("not_running")
            val phase = runCatching { darkircConnectionPhase() }.getOrDefault("stopped")
            if (isReadyForImmediateSend(status, phase)) {
                val wireBody =
                    DarkfiChatCrypto.encryptMessageIfPossible(
                        app,
                        normalized,
                        body,
                        delegateWireCryptoToDaemon = true,
                    )
                // Optimistic local bubble so public-channel sends are never invisible
                // while waiting for EventGraph self-echo / peer relay.
                val localId = recordLocalOutgoing(normalized, body)
                try {
                    DarkircCryptoManager.syncContactsToNative(app)
                    val nick = preferences.ensureIrcNickname()
                    sendChatMessage(normalized, nick, wireBody)
                } catch (e: Exception) {
                    retagOutgoing(normalized, localId, "failed")
                    Twig.error(e) { "FFI send failed" }
                    _diagnosticDetail.value = "Send failed: ${e.message ?: e.javaClass.simpleName}"
                }
                return@launch
            }

            outgoingQueue.enqueue(
                PendingOutgoingChatRecord(
                    channelNormalized = normalized,
                    textSanitized = body,
                    queuedAtEpochMs = System.currentTimeMillis(),
                ),
            )
            OutgoingChatReconnectWorker.schedule(app)

            val nick = preferences.ensureIrcNickname()
            val line =
                ChatChannelMessage(
                    eventId = "queued-${System.nanoTime()}",
                    channel = normalized,
                    nick = nick,
                    text = body,
                    timestampMs = System.currentTimeMillis(),
                    isOutgoing = true,
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
        /** When sharing a pubkey first, pass the same secret so the contact matches. */
        mySecretBase58: String? = null,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val label = contactLabel.trim().take(DmConversationLimits.CONTACT_LABEL_MAX_LEN)
                require(label.isNotBlank()) { "contact label required" }
                val theirPublic = theirPublicBase58.trim()
                val explicit = mySecretBase58?.trim()?.takeIf { it.isNotBlank() }
                val mySecret =
                    explicit
                        ?: DarkircCryptoManager.loadShareKeypair(app)?.myDmChachaSecretBase58
                        ?: if (generateMySecret) {
                            DarkircCryptoManager.ensureShareKeypair(app)?.myDmChachaSecretBase58
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
                NighthawkContactStore.upsert(
                    app,
                    NighthawkContact(
                        id = label.lowercase(java.util.Locale.ROOT),
                        nickname = label,
                        dmPublicKey = theirPublic,
                        walletAddress = NighthawkContactStore.findByNick(app, label)?.walletAddress,
                    ),
                    myDmSecret = mySecret,
                )
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
                NighthawkContactStore.remove(app, label)
                DmConversationStore.remove(app, label)
                _messagesByChannel.update { cur -> cur - label }
                refreshDmStateFromDisk()
            }
        }

    /**
     * Public key for "share pubkey" / new DM. The secret is stored and reused by
     * [addDmContact] so the displayed key is not thrown away for a different keypair.
     */
    fun generateEphemeralDmShareKeypair() = DarkircCryptoManager.ensureShareKeypair(app)

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
        private const val IDENTITY_NICK_PREFIX_LEN = 6
    }
}

private const val OPTIMISTIC_CLOCK_SKEW_BACKWARD_MS = 60_000L
private const val OPTIMISTIC_LOCAL_FORWARD_WINDOW_MS = 300_000L

internal fun chatTransportFor(network: PrivacyNetwork): DarkircTransport =
    when (network) {
        PrivacyNetwork.Nym -> DarkircTransport.NYM
        PrivacyNetwork.Tor -> DarkircTransport.TOR
        PrivacyNetwork.Direct -> DarkircTransport.DIRECT
    }

internal fun isReadyForImmediateSend(
    daemonStatus: String,
    connectionPhase: String,
): Boolean = daemonStatus == "running" && connectionPhase == "connected"

internal fun pruneOptimisticMatch(
    existing: List<ChatChannelMessage>,
    msg: ChatChannelMessage,
): List<ChatChannelMessage> {
    var dropped = false
    return existing.filterNot { line ->
        if (dropped) return@filterNot false
        val isOptimistic =
            line.eventId.startsWith("local-") ||
                line.eventId.startsWith("queued-") ||
                line.eventId.startsWith("failed-")
        val deltaMs = msg.timestampMs - line.timestampMs
        val withinWindow =
            if (line.eventId.startsWith("queued-")) {
                deltaMs >= -OPTIMISTIC_CLOCK_SKEW_BACKWARD_MS
            } else {
                deltaMs in -OPTIMISTIC_CLOCK_SKEW_BACKWARD_MS..OPTIMISTIC_LOCAL_FORWARD_WINDOW_MS
            }
        val matches =
            isOptimistic &&
                line.nick == msg.nick &&
                line.text == msg.text &&
                withinWindow
        if (matches) dropped = true
        matches
    }
}
