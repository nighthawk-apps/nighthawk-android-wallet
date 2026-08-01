package com.nighthawkapps.lib.android.sdk.wallet

import android.content.Context
import com.nighthawkapps.lib.android.sdk.uniffi.DarkfiNativeProbe
import com.nighthawkapps.lib.android.sdk.wallet.darkfid.DarkfidConnectionProbe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DarkfiWalletCoordinator internal constructor(
    private val context: Context,
    walletFlow: Flow<PersistableDarkfiWallet?>,
    private val useNativeSynchronizer: Boolean = DarkfiNativeProbe.run() is DarkfiNativeProbe.Ok,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val appContext = context.applicationContext

    val persistableWallet: StateFlow<PersistableDarkfiWallet?> =
        walletFlow
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    private val _synchronizer = MutableStateFlow<DarkfiSynchronizer?>(null)
    val synchronizer: StateFlow<DarkfiSynchronizer?> = _synchronizer

    init {
        scope.launch(Dispatchers.IO) {
            walletFlow.collectLatest { wallet ->
                _synchronizer.value =
                    wallet?.let { w ->
                        DarkfiSynchronizerFactory.create(w, appContext, useNativeSynchronizer)
                    }
            }
        }
    }

    fun rescanBlockchain() {
        scope.launch {
            synchronizer.value?.refreshNow()
        }
    }

    /** Recreates the synchronizer (e.g. after Tor toggle rewrites the darkfid connect URL). */
    fun reloadSynchronizer() {
        scope.launch(Dispatchers.IO) {
            val wallet = persistableWallet.value ?: return@launch
            _synchronizer.value =
                DarkfiSynchronizerFactory.create(wallet, appContext, useNativeSynchronizer)
        }
    }

    /** Probes darkfid JSON-RPC and maps result to synchronizer status (upstream DrkPlugin `connect`). */
    fun probeDarkfidAndUpdateStatus() {
        scope.launch(Dispatchers.IO) {
            val wallet = persistableWallet.value ?: return@launch
            val reachable = DarkfidConnectionProbe.isReachable(appContext, wallet.endpoint)
            synchronizer.value?.applyDarkfidReachability(reachable)
        }
    }

    fun resetSdk() {
        _synchronizer.value = null
    }

    companion object {
        fun create(
            context: Context,
            walletFlow: Flow<PersistableDarkfiWallet?>,
        ): DarkfiWalletCoordinator = DarkfiWalletCoordinator(context.applicationContext, walletFlow)
    }
}
