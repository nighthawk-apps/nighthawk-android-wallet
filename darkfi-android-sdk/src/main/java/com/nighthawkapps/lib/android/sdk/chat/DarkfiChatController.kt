@file:Suppress("MaxLineLength")

package com.nighthawkapps.lib.android.sdk.chat

import android.content.Context
import androidx.work.WorkManager
import com.nighthawkapps.lib.android.sdk.chat.darkirc.DarkircDaemonBootstrap
import com.nighthawkapps.lib.android.sdk.chat.darkirc.DarkircEmbeddedRunner
import com.nighthawkapps.lib.android.sdk.tor.embedded.EmbeddedTorController
import com.nighthawkapps.lib.android.spackle.Twig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetAddress

data class ChatChannelMessage(
    val channel: String,
    val nick: String,
    val text: String,
    val timestampMs: Long,
)

/**
 * Bridges the Compose chat UI to a real DarkIRC-compatible IRC session (TCP by default).
 *
 * DarkIRC itself (`darkfi/bin/darkirc`) hosts an IRC listener (typically `tcp://127.0.0.1:6667`)
 * while its P2P/EventGraph stack reaches lilith seeds (clearnet or Tor). With **embedded DarkIRC**
 * enabled, the APK can run the bundled `darkirc_exec` and this controller waits for the loopback IRC
 * listener before connecting.
 */
class DarkfiChatController(
    private val scope: CoroutineScope,
    appContext: Context,
) {
    private val app = appContext.applicationContext
    private val preferences = DarkfiChatPreferences(app)
    private val outgoingQueue = DarkfiChatOutgoingQueueStore.create(app)

    private val _connectionState = MutableStateFlow(DarkfiChatConnectionState.Disconnected)
    val connectionState: StateFlow<DarkfiChatConnectionState> = _connectionState.asStateFlow()

    private val _diagnosticDetail = MutableStateFlow<String?>(null)
    val diagnosticDetail: StateFlow<String?> = _diagnosticDetail.asStateFlow()

    private val _messagesByChannel = MutableStateFlow<Map<String, List<ChatChannelMessage>>>(emptyMap())
    val messagesByChannel: StateFlow<Map<String, List<ChatChannelMessage>>> = _messagesByChannel.asStateFlow()

    private var connectJob: Job? = null
    private var readJob: Job? = null
    private var irc: DarkircIrcClient? = null

    val defaultChannels: List<String> get() = DarkfiChatDefaults.DEFAULT_PUBLIC_CHANNELS

    var useTorTransport: Boolean
        get() = preferences.routeOutboundThroughTor
        set(value) {
            preferences.routeOutboundThroughTor = value
        }

    var ircServerHost: String
        get() = preferences.ircServerHost
        set(value) {
            preferences.ircServerHost = value
        }

    var ircServerPort: Int
        get() = preferences.ircServerPort
        set(value) {
            preferences.ircServerPort = value
        }

    /** IRC nick shown to others on channels; sanitized on write. */
    var ircNickname: String
        get() = preferences.ircNickname
        set(value) {
            preferences.ircNickname = DarkfiChatPreferences.sanitizeNickname(value)
        }

    var ircPassword: String?
        get() = preferences.ircPassword
        set(value) {
            preferences.ircPassword = value
        }

    var socksHost: String
        get() = preferences.socksHost
        set(value) {
            preferences.socksHost = value
        }

    var socksPort: Int
        get() = preferences.socksPort
        set(value) {
            preferences.socksPort = value
        }

    /**
     * Lilith URLs shown in diagnostics / DNS probes.
     *
     * When the **packaged embedded darkirc** runs, `[net]` always uses **clearnet `tcp+tls`** seeds
     * ([DarkircEmbeddedConfigGenerator]) — independent of the app “Tor” toggle (that drives SOCKS for
     * IRC/wallet HTTP, not the daemon’s P2P profile). Using [DarkfiChatDefaults.TOR_SEEDS] here would
     * mis-report `.onion` hosts and fail [InetAddress] probes even though the daemon never used them.
     */
    fun activeSeedUrls(): List<String> {
        val embeddedDaemonPackaged =
            preferences.runEmbeddedDarkirc && DarkircEmbeddedRunner.hasBundledBinary(app)
        if (embeddedDaemonPackaged) {
            return DarkfiChatDefaults.CLEARNET_SEEDS
        }
        return if (useTorTransport) DarkfiChatDefaults.TOR_SEEDS else DarkfiChatDefaults.CLEARNET_SEEDS
    }

    fun connectOrRetry() {
        connectJob?.cancel()
        readJob?.cancel()
        readJob = null
        irc?.close()
        irc = null

        connectJob =
            scope.launch(Dispatchers.IO) {
                runIrcConnectSequence()
            }
    }

    private suspend fun runIrcConnectSequence() {
        _connectionState.value = DarkfiChatConnectionState.Connecting
        _diagnosticDetail.value = null

        var rawHost = preferences.ircServerHost.trim().ifBlank { "127.0.0.1" }
        var useTls = false
        if (rawHost.startsWith("tcp+tls://")) {
            rawHost = rawHost.removePrefix("tcp+tls://")
            useTls = true
        } else if (rawHost.startsWith("tcp://")) {
            rawHost = rawHost.removePrefix("tcp://")
        }
        val host = rawHost
        val port = preferences.ircServerPort
        val nick = preferences.ensureIrcNickname()
        val pass = preferences.ircPassword

        val isLoopbackIrc =
            host == "127.0.0.1" ||
                host.equals("localhost", ignoreCase = true) ||
                host == "::1"

        val useSocks = shouldRouteIrcThroughTorSocks(useTorTransport, host)

        val socksHost =
            preferences.socksHost.trim().ifBlank { TorIntegrationHelper.DEFAULT_SOCKS_HOST }
        val socksPort = preferences.socksPort

        val torHints = StringBuilder()
        if (useTorTransport) {
            torHints.append(
                "Tor flag ON — SOCKS/wallet/HTTP routing; embedded DarkIRC P2P (if enabled) still uses " +
                    "clearnet lilith seeds: ${activeSeedUrls().joinToString()}.\n",
            )
            if (useSocks) {
                if (preferences.useEmbeddedTor) {
                    torHints.append(
                        "SOCKS endpoint $socksHost:$socksPort — bundled Tor (tor-android) " +
                            "starts in-app when connecting.\n",
                    )
                } else {
                    torHints.append(
                        "SOCKS endpoint $socksHost:$socksPort — external SOCKS only (built-in Tor off in " +
                            "Settings → Tor network).\n",
                    )
                }
            }
            if (isLoopbackIrc) {
                if (preferences.runEmbeddedDarkirc &&
                    DarkircEmbeddedRunner.hasBundledBinary(app)
                ) {
                    torHints.append(
                        "IRC host is loopback — SOCKS skipped. Packaged embedded DarkIRC should serve " +
                            "$host:$port.\n",
                    )
                } else {
                    torHints.append(
                        "IRC host is loopback — SOCKS skipped. Desktop tip: run darkirc then " +
                            "`adb reverse tcp:6667 tcp:6667`.\n",
                    )
                }
            }
        }

        if (!useTorTransport && isLoopbackIrc) {
            if (preferences.runEmbeddedDarkirc &&
                DarkircEmbeddedRunner.hasBundledBinary(app)
            ) {
                torHints.append(
                    "Clearnet IRC — loopback embedded DarkIRC should serve $host:$port (no SOCKS).\n",
                )
            } else if (preferences.runEmbeddedDarkirc) {
                torHints.append(
                    "Embedded DarkIRC is enabled but no darkirc_exec in APK — sync artifacts or use adb reverse.\n",
                )
            }
        }

        try {
            if (isLoopbackIrc && preferences.runEmbeddedDarkirc &&
                DarkircEmbeddedRunner.hasBundledBinary(app)
            ) {
                awaitEmbeddedBundledDarkircIrcTcp(host, port, torHints)
            }

            val socksPortForIrc =
                if (!useSocks) {
                    socksPort
                } else {
                    val socksSequenceDeadlineMs = SOCKS_SEQUENCE_DEADLINE_MS

                    val windowStartMs = System.currentTimeMillis()

                    val socksHint: Int? =
                        if (preferences.useEmbeddedTor) {
                            torHints.append(
                                "Embedded Tor: starting tor-android.\n",
                            )
                            EmbeddedTorController.awaitEmbeddedSocksIfEnabled(app, preferences).also { p ->
                                if (p != null) {
                                    torHints.append("Embedded Tor SOCKS reachable on port $p.\n")
                                } else {
                                    torHints.append(
                                        "Embedded Tor did not open SOCKS in time — probing configured ports.\n",
                                    )
                                }
                            }
                        } else {
                            null
                        }

                    val elapsedMs = System.currentTimeMillis() - windowStartMs
                    val probeBudgetMs =
                        (socksSequenceDeadlineMs - elapsedMs).coerceIn(
                            SOCKS_PROBE_MIN_REMAINING_MS,
                            socksSequenceDeadlineMs,
                        )

                    val socksPortsToProbe =
                        buildList {
                            if (socksHint != null) add(socksHint)
                            add(socksPort)
                            if (socksPort != TorIntegrationHelper.DEFAULT_SOCKS_PORT) {
                                add(TorIntegrationHelper.DEFAULT_SOCKS_PORT)
                            }
                            if (socksPort != TorIntegrationHelper.ALT_SOCKS_PORT) {
                                add(TorIntegrationHelper.ALT_SOCKS_PORT)
                            }
                        }.distinct()

                    torHints.append(
                        "Waiting for SOCKS at $socksHost ports ${socksPortsToProbe.joinToString()} " +
                            "(budget ${probeBudgetMs / MS_PER_SECOND}s).\n",
                    )
                    val effectiveSocksPort =
                        TorSocksReadiness.awaitFirstReachablePort(
                            socksHost,
                            socksPortsToProbe,
                            deadlineMs = probeBudgetMs,
                        )
                            ?: throw IOException(
                                "Tor SOCKS not reachable at $socksHost on ports " +
                                    "${socksPortsToProbe.joinToString()} within timeout. " +
                                    "If using embedded Tor, wait for bootstrap or check disk space; " +
                                    "otherwise ensure a SOCKS proxy is listening (try 9050/9150) " +
                                    "or use loopback IRC + adb reverse.",
                            )
                    if (effectiveSocksPort != socksPort) {
                        torHints.append("Using SOCKS port $effectiveSocksPort (configured was $socksPort).\n")
                    } else {
                        torHints.append("SOCKS port reachable ($effectiveSocksPort).\n")
                    }
                    effectiveSocksPort
                }

            val client =
                DarkircIrcClient(
                    DarkircConnectionConfig(
                        host = host,
                        port = port,
                        nickname = nick,
                        ircPassword = pass,
                        routeThroughTorSocks = useSocks,
                        socksHost = socksHost,
                        socksPort = socksPortForIrc,
                        useTls = useTls,
                    ),
                )
            irc = client
            client.connectAndJoin(defaultChannels)
            drainOutgoingQueue(client)

            readJob =
                scope.launch(Dispatchers.IO) {
                    try {
                        client.runReadLoop { rawMsg ->
                            val msg = DarkfiChatCrypto.decryptMessageIfPossible(app, rawMsg)
                            _messagesByChannel.update { cur ->
                                val next = cur.toMutableMap()
                                val key = msg.channel
                                next[key] = next[key].orEmpty() + msg
                                next
                            }
                        }
                    } catch (e: Exception) {
                        Twig.error(e) { "IRC read loop ended" }
                    } finally {
                        _connectionState.value = DarkfiChatConnectionState.Disconnected
                    }
                }

            mergeDefaultChannelPlaceholders()

            val terminal =
                when {
                    useSocks -> DarkfiChatConnectionState.ConnectedViaTor
                    else -> DarkfiChatConnectionState.ConnectedDirect
                }
            _connectionState.value = terminal

            val dnsDiag = seedDnsDiagnostics()

            _diagnosticDetail.value =
                buildString {
                    append(torHints)
                    append("IRC session $host:$port as '$nick'.\n")
                    append(dnsDiag)
                    append(
                        "\nNative note: DarkFi’s daemon embeds Arti for Tor transports; this APK uses " +
                            "loopback SOCKS (bundled tor-android or your configured proxy) for IRC hops until JNI " +
                            "links `darkirc`.\n",
                    )
                }

            Twig.info { "DarkIRC chat ready $host:$port socks=$useSocks" }
        } catch (e: Exception) {
            Twig.error(e) { "DarkIRC connect failed" }
            _connectionState.value = DarkfiChatConnectionState.Error
            readJob?.cancel()
            readJob = null
            irc?.close()
            irc = null
            val tipWhenEmbedded =
                "Tip: packaged embedded DarkIRC should listen on loopback — inspect logcat / SELinux, " +
                    "DAG sync time, or confirm artifacts sync.\n"
            val tipLoopbackManual =
                "Tip: run darkirc on a host then `adb reverse tcp:6667 tcp:6667`, or expose IRC remotely.\n"
            val tipGeneric =
                "Tip: reach darkirc IRC at $host:$port (workstation daemon, tunnel, or embedded binary).\n"
            val connectTip =
                when {
                    isLoopbackIrc && preferences.runEmbeddedDarkirc &&
                        DarkircEmbeddedRunner.hasBundledBinary(app) -> {
                        tipWhenEmbedded
                    }

                    isLoopbackIrc -> {
                        tipLoopbackManual
                    }

                    else -> {
                        tipGeneric
                    }
                }
            _diagnosticDetail.value =
                buildString {
                    append(torHints)
                    append("IRC connect error: ${e.javaClass.simpleName}: ${e.message}\n")
                    append(connectTip)
                    append(seedDnsDiagnostics())
                }
        }
    }

    private suspend fun awaitEmbeddedBundledDarkircIrcTcp(
        host: String,
        port: Int,
        torHints: StringBuilder,
    ) {
        torHints.append("Embedded DarkIRC: syncing `irc_listen` with settings and starting foreground / process...\n")
        val started = DarkircDaemonBootstrap.prepareForLoopbackIrc(app)
        if (!started) {
            torHints.append(
                "Embedded DarkIRC: process did not spawn; still waiting on IRC TCP...\n",
            )
        }
        torHints.append(
            "Embedded DarkIRC: probing IRC TCP $host:$port " +
                "(deadline ${DARKIRC_IRC_LISTENER_DEADLINE_MS / MS_PER_SECOND}s).\n",
        )
        val listenerOk =
            TorSocksReadiness.awaitTcpReachable(
                host,
                port,
                deadlineMs = DARKIRC_IRC_LISTENER_DEADLINE_MS,
                pollMs = 750L,
            )
        if (!listenerOk) {
            throw IOException(
                "Embedded DarkIRC did not open IRC on $host:$port within " +
                    "${DARKIRC_IRC_LISTENER_DEADLINE_MS / MS_PER_SECOND}s. " +
                    "Package artifacts/darkirc/<abi>/darkirc_exec (Gradle syncDarkircArtifacts), " +
                    "check logcat tags darkirc / DarkfiChat, or disable “Run embedded DarkIRC”.",
            )
        }
        torHints.append("Embedded DarkIRC: IRC TCP is reachable.\n")
    }

    private suspend fun seedDnsDiagnostics(): String =
        withContext(Dispatchers.IO) {
            val seeds = activeSeedUrls()
            val sb = StringBuilder()
            sb.append("Seed DNS sanity:\n")
            for (seed in seeds) {
                val hostPart = extractHostFromSeed(seed)
                if (hostPart.isNullOrBlank()) {
                    sb.append("- $seed → parse error\n")
                    continue
                }
                try {
                    val ip = InetAddress.getByName(hostPart).hostAddress
                    sb.append("- $hostPart → $ip\n")
                } catch (e: Exception) {
                    sb.append("- $hostPart → FAILED (${e.javaClass.simpleName})\n")
                }
            }
            sb.toString()
        }

    private fun drainOutgoingQueue(client: DarkircIrcClient) {
        val pending = outgoingQueue.loadAll()
        if (pending.isEmpty()) return
        for (rec in pending) {
            try {
                client.sendPrivmsg(rec.channelNormalized, rec.textSanitized)
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

    fun sendToChannel(
        channel: String,
        text: String
    ) {
        val normalized = if (channel.startsWith("#")) channel else channel // If not a channel, keep as nick for DM
        val body = DarkircPrivmsgRules.sanitizeBody(text) ?: return
        val encryptedBody = DarkfiChatCrypto.encryptMessageIfPossible(app, normalized, body)
        val session = irc
        if (session?.isReady() == true) {
            scope.launch(Dispatchers.IO) {
                try {
                    session.sendPrivmsg(normalized, encryptedBody)
                } catch (e: Exception) {
                    Twig.error(e) { "PRIVMSG failed" }
                }
            }
            return
        }

        outgoingQueue.enqueue(
            PendingOutgoingChatRecord(
                channelNormalized = normalized,
                textSanitized = body, // Queued text is unencrypted because we encrypt on send if possible? Or encrypt before queueing? Better to encrypt when sending!
                queuedAtEpochMs = System.currentTimeMillis(),
            ),
        )
        OutgoingChatReconnectWorker.schedule(app)

        scope.launch(Dispatchers.IO) {
            Twig.warn { "IRC offline — queued for send: $normalized" }
            val line =
                ChatChannelMessage(
                    channel = normalized,
                    nick = "queued",
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

    fun stop() {
        connectJob?.cancel()
        connectJob = null
        readJob?.cancel()
        readJob = null
        irc?.close()
        irc = null
        _connectionState.value = DarkfiChatConnectionState.Disconnected
        _diagnosticDetail.value = null
    }

    private fun mergeDefaultChannelPlaceholders() {
        _messagesByChannel.update { cur ->
            val next = cur.toMutableMap()
            for (ch in defaultChannels) {
                if (!next.containsKey(ch)) {
                    next[ch] = emptyList()
                }
            }
            next
        }
    }

    companion object {
        private const val SOCKS_SEQUENCE_DEADLINE_MS = 90_000L
        private const val SOCKS_PROBE_MIN_REMAINING_MS = 5_000L
        private const val MS_PER_SECOND = 1_000
        private const val DARKIRC_IRC_LISTENER_DEADLINE_MS = 180_000L

        fun extractHostFromSeed(seedUrl: String): String? {
            val schemeIdx = seedUrl.indexOf("://")
            if (schemeIdx < 0) return null
            val rest = seedUrl.substring(schemeIdx + 3)
            val colon = rest.indexOf(':')
            val slash = rest.indexOf('/')
            val end =
                when {
                    colon >= 0 && slash >= 0 -> minOf(colon, slash)
                    colon >= 0 -> colon
                    slash >= 0 -> slash
                    else -> rest.length
                }
            if (end <= 0) return null
            return rest.substring(0, end).takeIf { it.isNotBlank() }
        }
    }
}
