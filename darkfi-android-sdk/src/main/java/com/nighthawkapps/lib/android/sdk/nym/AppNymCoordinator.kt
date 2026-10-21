package com.nighthawkapps.lib.android.sdk.nym

import android.content.Context
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatPreferences
import com.nighthawkapps.lib.android.sdk.net.PrivacyNetwork
import com.nighthawkapps.lib.android.sdk.tor.AppTorCoordinator
import com.nighthawkapps.lib.android.sdk.tor.TorBootstrapUiState
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiWalletCoordinator
import com.nighthawkapps.lib.android.spackle.Twig
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.nymStatus
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.startNymProxy
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.stopNymProxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * App-wide lifecycle of the in-process Nym mixnet SOCKS proxy, the default wallet network.
 *
 * Rust also starts the proxy when a wallet opens with `useNym`; starting it here at launch lets the
 * splash show mixnet progress and lets HTTP clients use the port before the wallet opens.
 */
object AppNymCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _bootstrapState = MutableStateFlow(TorBootstrapUiState.Idle)
    val bootstrapState: StateFlow<TorBootstrapUiState> = _bootstrapState.asStateFlow()

    /** Wall-clock start of the current bootstrap attempt, for elapsed-time progress in the UI. */
    private val _bootstrapStartedAtMs = MutableStateFlow(0L)
    val bootstrapStartedAtMs: StateFlow<Long> = _bootstrapStartedAtMs.asStateFlow()

    /** Non-blocking: start the Nym proxy when Nym is the wallet network. */
    fun warmStartIfEnabled(context: Context) {
        val prefs = DarkfiChatPreferences(context.applicationContext)
        if (prefs.privacyNetwork != PrivacyNetwork.Nym) {
            _bootstrapState.value = TorBootstrapUiState.Disabled
            return
        }
        markBootstrapping()
        scope.launch { ensureReady(context) }
    }

    /**
     * Starts the proxy and waits for the mixnet tunnel. Returns the SOCKS port, or null when Nym is
     * not the wallet network or bootstrap failed.
     */
    suspend fun ensureReady(context: Context): Int? {
        val prefs = DarkfiChatPreferences(context.applicationContext)
        if (prefs.privacyNetwork != PrivacyNetwork.Nym) {
            _bootstrapState.value = TorBootstrapUiState.Disabled
            return null
        }
        val port = prefs.nymSocksPort
        if (_bootstrapState.value != TorBootstrapUiState.Ready) {
            markBootstrapping()
        }
        val started =
            PlatformTls.ensureInitialized(context) &&
                runCatching { startNymProxy(port.toString()) }
                    .onFailure { Twig.warn { "AppNym: start failed: $it" } }
                    .isSuccess
        val deadline = System.currentTimeMillis() + NYM_BOOTSTRAP_DEADLINE_MS
        var ready = false
        while (started && System.currentTimeMillis() < deadline) {
            if (prefs.privacyNetwork != PrivacyNetwork.Nym) {
                break
            }
            when (runCatching { nymStatus() }.getOrDefault(NYM_STATUS_STOPPED)) {
                NYM_STATUS_CONNECTED, NYM_STATUS_RECONNECTING -> {
                    ready = true
                    break
                }
                // The native bootstrap gave up; surface Retry now instead of at the deadline.
                NYM_STATUS_FAILED -> break
            }
            delay(500)
        }
        _bootstrapState.value =
            when {
                prefs.privacyNetwork != PrivacyNetwork.Nym -> TorBootstrapUiState.Disabled
                ready -> TorBootstrapUiState.Ready
                else -> TorBootstrapUiState.Failed
            }
        if (ready) {
            Twig.info { "AppNym: mixnet tunnel ready on port $port" }
        } else if (_bootstrapState.value == TorBootstrapUiState.Failed) {
            Twig.warn { "AppNym: mixnet bootstrap failed or timed out" }
        }
        return port.takeIf { ready }
    }

    private fun markBootstrapping() {
        if (_bootstrapState.value != TorBootstrapUiState.Bootstrapping || _bootstrapStartedAtMs.value == 0L) {
            _bootstrapStartedAtMs.value = System.currentTimeMillis()
        }
        _bootstrapState.value = TorBootstrapUiState.Bootstrapping
    }

    /**
     * Splash escape hatch: switch the app to a direct connection immediately. Callers holding a
     * wallet coordinator should follow up with [applyPrivacyNetwork] so P2P and the wallet move too.
     */
    fun disableFromSplash(context: Context) {
        DarkfiChatPreferences(context.applicationContext).privacyNetwork = PrivacyNetwork.Direct
        runCatching { stopNymProxy() }
        _bootstrapState.value = TorBootstrapUiState.Disabled
        Twig.info { "AppNym: disabled from splash; wallet traffic goes direct" }
    }

    /**
     * Settings change: persist the app network, bring up the matching proxy (stopping the others),
     * move DarkIRC / darkfid P2P onto it, and reopen the wallet.
     */
    suspend fun applyPrivacyNetwork(
        context: Context,
        network: PrivacyNetwork,
        walletCoordinator: DarkfiWalletCoordinator,
    ) {
        val app = context.applicationContext
        DarkfiChatPreferences(app).privacyNetwork = network
        if (network == PrivacyNetwork.Nym) {
            ensureReady(app)
        } else {
            runCatching { stopNymProxy() }
            _bootstrapState.value = TorBootstrapUiState.Disabled
        }
        if (network == PrivacyNetwork.Tor) {
            AppTorCoordinator.ensureSocksReady(app)
        } else {
            AppTorCoordinator.stopEmbeddedTor()
        }
        AppTorCoordinator.restartEmbeddedDarkircForTransport(app)
        AppTorCoordinator.restartEmbeddedDarkfidForTransport(app)
        walletCoordinator.reloadSynchronizer()
        walletCoordinator.rescanBlockchain()
    }

    const val NYM_STATUS_STOPPED = "stopped"
    const val NYM_STATUS_CONNECTED = "connected"
    const val NYM_STATUS_RECONNECTING = "reconnecting"
    const val NYM_STATUS_FAILED = "failed"

    /** Topology fetch, gateway handshake, and IPR connect can exceed a minute on mobile data. */
    private const val NYM_BOOTSTRAP_DEADLINE_MS = 120_000L
}
