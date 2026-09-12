package com.nighthawkapps.lib.android.sdk.daemon

import android.content.Context
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatConnectionBridge
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatPreferences
import com.nighthawkapps.lib.android.sdk.tor.AppTorCoordinator
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiSynchronizer
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiWalletCoordinator
import com.nighthawkapps.lib.android.sdk.wallet.darkfid.DarkfidConnectionProbe
import com.nighthawkapps.lib.android.sdk.wallet.darkfid.DarkfidDaemonBootstrap
import com.nighthawkapps.lib.android.spackle.Twig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns app-start bootstrap and periodic reconnect for embedded darkirc + wallet scan/subscribe.
 *
 * Wallet business logic stays in Rust (`drk` / UniFFI); chat P2P stays in bundled `darkirc_exec`.
 * This class only orchestrates process lifecycle and exposes one status stream to the UI.
 */
class DarkfiDaemonLifecycleCoordinator private constructor(
    private val appContext: Context,
    private val walletCoordinatorProvider: () -> DarkfiWalletCoordinator,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val chatPrefs by lazy { DarkfiChatPreferences(appContext) }
    private val daemonPrefs by lazy { DarkfiDaemonPreferences(appContext) }

    private val _status = MutableStateFlow(DarkfiDaemonStatus.Starting)
    val status: StateFlow<DarkfiDaemonStatus> = _status.asStateFlow()

    private var reconnectJob: Job? = null
    private var bootstrapComplete = false

    fun start() {
        scope.launch {
            bootstrapComplete = false
            _status.value = DarkfiDaemonStatus.Starting
            AppTorCoordinator.ensureSocksReady(appContext)
            DarkfidDaemonBootstrap.maybeStart(appContext)
            if (daemonPrefs.runEmbeddedDarkfid) {
                DarkfidDaemonBootstrap.prepareLoopbackRpc(appContext)
                awaitEmbeddedDarkfidIfNeeded()
            }
            if (chatPrefs.runEmbeddedDarkirc) {
                com.nighthawkapps.lib.android.sdk.chat.darkirc.DarkircDaemonService
                    .start(appContext)
            }
            bootstrapComplete = true
            observeSubsystems()
            startReconnectLoop()
        }
    }

    private suspend fun awaitEmbeddedDarkfidIfNeeded() {
        val wallet = walletCoordinatorProvider().persistableWallet.value ?: return
        val ok = DarkfidConnectionProbe.awaitLoopbackRpc(appContext, wallet.endpoint)
        walletCoordinatorProvider().probeDarkfidAndUpdateStatus()
        if (!ok) {
            Twig.warn { "Embedded darkfid: JSON-RPC not reachable at ${wallet.endpoint.toDisplayString()}" }
        }
    }

    /** Manual restart from home indicator or Settings after endpoint change. */
    fun restartConnection() {
        scope.launch {
            _status.value = DarkfiDaemonStatus.Connecting
            Twig.info { "DarkFi daemon: manual restart requested" }
            AppTorCoordinator.ensureSocksReady(appContext)
            if (daemonPrefs.runEmbeddedDarkfid) {
                DarkfidDaemonBootstrap.restartEmbedded(appContext)
            } else {
                DarkfidDaemonBootstrap.stop(appContext)
            }
            if (chatPrefs.runEmbeddedDarkirc) {
                com.nighthawkapps.lib.android.sdk.chat.darkirc.DarkircDaemonService
                    .start(appContext)
            } else {
                com.nighthawkapps.lib.android.sdk.chat.darkirc.DarkircDaemonService
                    .stop(appContext)
            }
            walletCoordinatorProvider().reloadSynchronizer()
            walletCoordinatorProvider().rescanBlockchain()
            DarkfiChatConnectionBridge.requestReconnect()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeSubsystems() {
        scope.launch {
            walletCoordinatorProvider()
                .synchronizer
                .flatMapLatest { sync ->
                    statusFlowFor(sync)
                }.distinctUntilChanged()
                .collectLatest { mapped ->
                    _status.value = mapped
                }
        }
    }

    private fun statusFlowFor(sync: DarkfiSynchronizer?): Flow<DarkfiDaemonStatus> {
        if (sync == null) {
            return flowOf(
                DarkfiDaemonStatusMapping.map(
                    walletPresent = false,
                    walletStatus = null,
                    walletHasError = false,
                    chatState = DarkfiChatStatusRegistry.state.value,
                    embeddedDarkircEnabled = chatPrefs.runEmbeddedDarkirc,
                    bootstrapComplete = bootstrapComplete,
                ),
            )
        }
        return combine(
            sync.status,
            sync.walletErrors,
            DarkfiChatStatusRegistry.state,
            walletCoordinatorProvider().persistableWallet,
        ) { walletStatus, walletError, chatState, wallet ->
            DarkfiDaemonStatusMapping.map(
                walletPresent = wallet != null,
                walletStatus = walletStatus,
                walletHasError = walletError != null,
                chatState = chatState,
                embeddedDarkircEnabled = chatPrefs.runEmbeddedDarkirc,
                bootstrapComplete = bootstrapComplete,
            )
        }
    }

    /**
     * Upstream `DARKFID_RETRY_TIME` ≈ 20s between scan/subscribe attempts.
     *
     * IRC reconnect is **not** forced on every tick — only when [DarkfiChatConnectionState.needsIrcReconnectNudge]
     * so a healthy read loop is not torn down every 20s.
     */
    private fun startReconnectLoop() {
        reconnectJob?.cancel()
        reconnectJob =
            scope.launch {
                while (isActive) {
                    delay(RECONNECT_INTERVAL_MS)
                    if (chatPrefs.routeOutboundThroughTor) {
                        AppTorCoordinator.ensureSocksReady(appContext)
                    }
                    if (daemonPrefs.runEmbeddedDarkfid) {
                        DarkfidDaemonBootstrap.warmOnForeground(appContext)
                    }
                    walletCoordinatorProvider().probeDarkfidAndUpdateStatus()
                    if (_status.value != DarkfiDaemonStatus.Connected) {
                        Twig.debug { "DarkFi daemon: not connected — refresh" }
                        walletCoordinatorProvider().rescanBlockchain()
                    }
                    // Keep-alive FGS: start() is a no-op when already active, and
                    // swallows background-start denials until the next foreground resume.
                    if (chatPrefs.runEmbeddedDarkirc) {
                        com.nighthawkapps.lib.android.sdk.chat.darkirc.DarkircDaemonService
                            .start(appContext)
                    }
                    nudgeIrcReconnectIfNeeded()
                }
            }
    }

    private fun nudgeIrcReconnectIfNeeded() {
        val chatState = DarkfiChatStatusRegistry.state.value
        if (!chatState.needsIrcReconnectNudge()) {
            Twig.debug { "DarkFi daemon: IRC session healthy ($chatState) — skip reconnect nudge" }
            return
        }
        if (DarkfiChatConnectionBridge.requestReconnect()) {
            Twig.debug { "DarkFi daemon: IRC reconnect nudged ($chatState)" }
        }
    }

    companion object {
        private const val RECONNECT_INTERVAL_MS = 20_000L

        @Volatile
        private var instance: DarkfiDaemonLifecycleCoordinator? = null

        fun install(
            context: Context,
            walletCoordinatorProvider: () -> DarkfiWalletCoordinator,
        ): DarkfiDaemonLifecycleCoordinator =
            instance ?: synchronized(this) {
                instance ?: DarkfiDaemonLifecycleCoordinator(
                    context.applicationContext,
                    walletCoordinatorProvider,
                ).also {
                    instance = it
                    it.start()
                }
            }

        fun get(): DarkfiDaemonLifecycleCoordinator =
            instance ?: error(
                "DarkfiDaemonLifecycleCoordinator not installed",
            )
    }
}
