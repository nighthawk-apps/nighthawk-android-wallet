package com.nighthawkapps.lib.android.sdk.tor

import android.content.Context
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatConnectionBridge
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatPreferences
import com.nighthawkapps.lib.android.sdk.chat.TorIntegrationHelper
import com.nighthawkapps.lib.android.sdk.chat.TorSocksReadiness
import com.nighthawkapps.lib.android.sdk.chat.darkirc.DarkircDaemonService
import com.nighthawkapps.lib.android.sdk.chat.darkirc.DarkircP2pTransport
import com.nighthawkapps.lib.android.sdk.daemon.DarkfiDaemonPreferences
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiWalletCoordinator
import com.nighthawkapps.lib.android.sdk.wallet.darkfid.DarkfidDaemonBootstrap
import com.nighthawkapps.lib.android.sdk.wallet.darkfid.DarkfidEmbeddedRunner
import com.nighthawkapps.lib.android.sdk.wallet.darkfid.DarkfidP2pTransport
import com.nighthawkapps.lib.android.spackle.Twig
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.isArtiRunning
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.startArtiProxy
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.stopArtiProxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Splash / Settings-facing Tor bootstrap progress (embedded Arti or external SOCKS). */
enum class TorBootstrapUiState {
    /** Not observed yet (before [AppTorCoordinator.warmStartIfEnabled]). */
    Idle,

    /** User turned Tor off — splash should not wait. */
    Disabled,

    /** Arti / SOCKS coming up. */
    Bootstrapping,

    /** SOCKS ready (`isArtiRunning` or external probe). */
    Ready,

    /** Timeout or start failure — splash may dismiss; chat/wallet can retry. */
    Failed,
}

/**
 * App-wide Tor lifecycle (Guardian tor-android / Arti SOCKS), modeled after wallets that route
 * outbound traffic through a local SOCKS proxy when the user enables Tor.
 *
 * Chat P2P uses in-process UniFFI darkirc with `use_tor` / SOCKS; wallet darkfid uses its own
 * embedded runner when enabled.
 */
object AppTorCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _bootstrapState = MutableStateFlow(TorBootstrapUiState.Idle)
    val bootstrapState: StateFlow<TorBootstrapUiState> = _bootstrapState.asStateFlow()

    /**
     * Do not pin the system splash on Tor — Compose must stay interactive so the user can
     * tap “Continue without Tor” if bootstrap stalls.
     */
    fun isKeepingSplash(): Boolean = false

    /**
     * Splash escape hatch: persist Tor off, stop Arti, continue over clearnet.
     * In-flight [ensureSocksReady] will observe the pref and abort.
     */
    fun disableTorFromSplash(context: Context) {
        val app = context.applicationContext
        val prefs = DarkfiChatPreferences(app)
        prefs.routeOutboundThroughTor = false
        runCatching { stopArtiProxy() }
        _bootstrapState.value = TorBootstrapUiState.Disabled
        Twig.info { "AppTor: disabled from splash — using clearnet" }
    }

    /** Non-blocking: start embedded Tor when prefs demand it (Application / daemon coordinator). */
    fun warmStartIfEnabled(context: Context) {
        val prefs = DarkfiChatPreferences(context.applicationContext)
        if (!prefs.routeOutboundThroughTor) {
            _bootstrapState.value = TorBootstrapUiState.Disabled
            return
        }
        _bootstrapState.value = TorBootstrapUiState.Bootstrapping
        scope.launch {
            ensureSocksReady(context)
        }
    }

    /**
     * Blocks until SOCKS is reachable (embedded or external), or returns null when Tor routing is off
     * or probes time out.
     */
    suspend fun ensureSocksReady(context: Context): Int? {
        val app = context.applicationContext
        val prefs = DarkfiChatPreferences(app)
        val port =
            when {
                !prefs.routeOutboundThroughTor -> {
                    _bootstrapState.value = TorBootstrapUiState.Disabled
                    null
                }

                prefs.useEmbeddedTor -> {
                    if (_bootstrapState.value != TorBootstrapUiState.Ready) {
                        _bootstrapState.value = TorBootstrapUiState.Bootstrapping
                    }
                    Twig.info { "AppTor: starting in-process Arti proxy on port ${prefs.socksPort}..." }
                    val started = startArtiProxy(prefs.socksPort.toString())
                    if (started) {
                        // Arti boots asynchronously via UniFFI thread; wait briefly before nudging SOCKS
                        delay(500)
                    }
                    if (!prefs.routeOutboundThroughTor) {
                        null
                    } else {
                        // TCP bind can succeed before Tor circuits are ready (listener opens first).
                        // Wait for real bootstrap via isArtiRunning before handing the port to darkirc / LWD.
                        val bound =
                            TorSocksReadiness.awaitFirstReachablePort(
                                "127.0.0.1",
                                listOf(prefs.socksPort),
                                deadlineMs = EXTERNAL_SOCKS_DEADLINE_MS,
                            )
                        if (bound == null || !prefs.routeOutboundThroughTor) {
                            if (bound == null && prefs.routeOutboundThroughTor) {
                                Twig.warn { "AppTor: Arti SOCKS bind timed out" }
                            }
                            null
                        } else {
                            val bootstrapDeadline = System.currentTimeMillis() + ARTI_BOOTSTRAP_DEADLINE_MS
                            var readyPort: Int? = null
                            while (System.currentTimeMillis() < bootstrapDeadline) {
                                if (!prefs.routeOutboundThroughTor) {
                                    break
                                }
                                if (runCatching { isArtiRunning() }.getOrDefault(false)) {
                                    Twig.info { "AppTor: Arti bootstrapped on port $bound" }
                                    readyPort = bound
                                    break
                                }
                                delay(500)
                            }
                            if (readyPort == null && prefs.routeOutboundThroughTor) {
                                Twig.warn { "AppTor: Arti SOCKS bound on $bound but bootstrap timed out" }
                            }
                            readyPort
                        }
                    }
                }

                else -> {
                    if (_bootstrapState.value != TorBootstrapUiState.Ready) {
                        _bootstrapState.value = TorBootstrapUiState.Bootstrapping
                    }
                    val host =
                        prefs.socksHost.trim().ifBlank { TorIntegrationHelper.DEFAULT_SOCKS_HOST }
                    val ports =
                        buildList {
                            add(prefs.socksPort)
                            if (prefs.socksPort != TorIntegrationHelper.DEFAULT_SOCKS_PORT) {
                                add(TorIntegrationHelper.DEFAULT_SOCKS_PORT)
                            }
                            if (prefs.socksPort != TorIntegrationHelper.ALT_SOCKS_PORT) {
                                add(TorIntegrationHelper.ALT_SOCKS_PORT)
                            }
                        }.distinct()
                    TorSocksReadiness.awaitFirstReachablePort(
                        host,
                        ports,
                        deadlineMs = EXTERNAL_SOCKS_DEADLINE_MS,
                    )
                }
            }
        // Re-read prefs: splash may have disabled Tor while we were waiting.
        val stillWantsTor = DarkfiChatPreferences(app).routeOutboundThroughTor
        when {
            !stillWantsTor -> _bootstrapState.value = TorBootstrapUiState.Disabled
            port != null -> _bootstrapState.value = TorBootstrapUiState.Ready
            else -> _bootstrapState.value = TorBootstrapUiState.Failed
        }
        return if (stillWantsTor) port else null
    }

    /**
     * Settings toggle or profile change: start/stop embedded Tor and restart embedded darkirc so P2P
     * profile matches upstream `use_tor.txt` semantics.
     */
    suspend fun applyTorRoutingEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        val app = context.applicationContext
        val prefs = DarkfiChatPreferences(app)
        prefs.routeOutboundThroughTor = enabled
        if (enabled) {
            ensureSocksReady(app)
        } else {
            stopArtiProxy()
            _bootstrapState.value = TorBootstrapUiState.Disabled
        }
        restartEmbeddedDarkircForTransport(app)
        restartEmbeddedDarkfidForTransport(app)
    }

    /**
     * Full stack refresh after Tor routing change: darkirc P2P profile + wallet native reconnect.
     */
    suspend fun applyTorRoutingEnabledWithWalletReload(
        context: Context,
        enabled: Boolean,
        walletCoordinator: DarkfiWalletCoordinator,
    ) {
        applyTorRoutingEnabled(context, enabled)
        walletCoordinator.reloadSynchronizer()
        walletCoordinator.rescanBlockchain()
    }

    fun resolveDarkircP2pTransport(context: Context): DarkircP2pTransport {
        val prefs = DarkfiChatPreferences(context.applicationContext)
        return if (prefs.routeOutboundThroughTor) {
            DarkircP2pTransport.TorViaSocks5(
                socksHost =
                    prefs.socksHost.trim().ifBlank { TorIntegrationHelper.DEFAULT_SOCKS_HOST },
                socksPort = prefs.socksPort,
            )
        } else {
            DarkircP2pTransport.Clearnet
        }
    }

    fun resolveDarkfidP2pTransport(context: Context): DarkfidP2pTransport {
        val prefs = DarkfiChatPreferences(context.applicationContext)
        return if (prefs.routeOutboundThroughTor) {
            DarkfidP2pTransport.TorViaSocks5(
                socksHost =
                    prefs.socksHost.trim().ifBlank { TorIntegrationHelper.DEFAULT_SOCKS_HOST },
                socksPort = prefs.socksPort,
            )
        } else {
            DarkfidP2pTransport.Clearnet
        }
    }

    /** Restart UniFFI darkirc when Tor/clearnet P2P profile changes. */
    fun restartEmbeddedDarkircForTransport(context: Context) {
        val app = context.applicationContext
        val prefs = DarkfiChatPreferences(app)
        if (!prefs.runEmbeddedDarkirc) {
            return
        }
        Twig.info { "AppTor: restarting embedded darkirc for P2P transport ${resolveDarkircP2pTransport(app)}" }
        DarkircDaemonService.start(app)
        DarkfiChatConnectionBridge.requestReconnect()
    }

    fun restartEmbeddedDarkfidForTransport(context: Context) {
        val app = context.applicationContext
        val prefs = DarkfiDaemonPreferences(app)
        if (!prefs.runEmbeddedDarkfid || !DarkfidEmbeddedRunner.hasBundledBinary(app)) {
            return
        }
        Twig.info { "AppTor: restarting embedded darkfid for P2P ${resolveDarkfidP2pTransport(app)}" }
        DarkfidDaemonBootstrap.restartEmbedded(app)
    }

    /**
     * After SOCKS host/port or built-in Tor toggle changes (Settings → Tor network), refresh Tor SOCKS
     * and restart embedded darkirc so P2P/event-graph transport matches prefs, then nudge IRC reconnect.
     */
    suspend fun applyNetworkProfileChange(context: Context) {
        val app = context.applicationContext
        val prefs = DarkfiChatPreferences(app)
        if (prefs.routeOutboundThroughTor && prefs.useEmbeddedTor) {
            ensureSocksReady(app)
        } else if (!prefs.useEmbeddedTor) {
            stopArtiProxy()
        }
        restartEmbeddedDarkircForTransport(app)
        DarkfiChatConnectionBridge.requestReconnect()
    }

    private const val EXTERNAL_SOCKS_DEADLINE_MS = 30_000L

    /** First-launch Arti directory + circuit setup can exceed the TCP bind wait. */
    private const val ARTI_BOOTSTRAP_DEADLINE_MS = 120_000L
}
