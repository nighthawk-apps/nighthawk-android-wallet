package com.nighthawkapps.lib.android.ui.screen.home.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nighthawkapps.lib.android.global.AppWalletCoordinator
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiEndpoint
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiEndpointNetworkGuard
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiSpendKey
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiSyncStatus
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiSynchronizer
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiTransactionOverview
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiWalletAddresses
import com.nighthawkapps.lib.android.sdk.wallet.PersistableDarkfiWallet
import com.nighthawkapps.lib.android.sdk.wallet.WalletInitMode
import com.nighthawkapps.lib.android.sdk.wallet.darkfiNetworkFromPackage
import com.nighthawkapps.lib.android.spackle.Twig
import com.nighthawkapps.lib.android.ui.common.ANDROID_STATE_FLOW_TIMEOUT
import com.nighthawkapps.lib.android.ui.common.throttle
import com.nighthawkapps.lib.android.ui.preference.EncryptedPreferenceKeys
import com.nighthawkapps.lib.android.ui.preference.EncryptedPreferenceSingleton
import com.nighthawkapps.lib.android.ui.preference.StandardPreferenceKeys
import com.nighthawkapps.lib.android.ui.preference.StandardPreferenceSingleton
import com.nighthawkapps.lib.android.ui.screen.fiatcurrency.model.FiatCurrency
import com.nighthawkapps.lib.android.ui.screen.history.state.TransactionHistorySyncState
import com.nighthawkapps.lib.android.ui.screen.home.model.WalletSnapshot
import com.nighthawkapps.lib.android.ui.security.WalletPinSecureStore
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

class WalletViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val walletCoordinator = AppWalletCoordinator.get(application)

    private val persistWalletMutex = Mutex()

    private val _endpointSaveError = MutableStateFlow<String?>(null)
    val endpointSaveError: StateFlow<String?> = _endpointSaveError

    private val _createWalletError = MutableStateFlow<String?>(null)
    val createWalletError: StateFlow<String?> = _createWalletError

    val synchronizer: StateFlow<DarkfiSynchronizer?> =
        walletCoordinator.synchronizer.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT.inWholeMilliseconds),
            null,
        )

    val preferredFiatCurrency: StateFlow<FiatCurrency?> =
        flow<FiatCurrency?> {
            val preferenceProvider = StandardPreferenceSingleton.getInstance(application)
            emitAll(StandardPreferenceKeys.PREFERRED_FIAT_CURRENCY.observe(preferenceProvider))
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT.inWholeMilliseconds),
            null,
        )

    private val isBackupComplete =
        flow {
            val preferenceProvider = StandardPreferenceSingleton.getInstance(application)
            emitAll(StandardPreferenceKeys.IS_USER_BACKUP_COMPLETE.observe(preferenceProvider))
        }

    private val isUserAuthenticated = MutableStateFlow(false)

    private val isAuthenticationRequire =
        flow {
            emit(
                withContext(Dispatchers.IO) {
                    WalletPinSecureStore.isConfigured(application)
                },
            )
        }.combine(isUserAuthenticated) { isAuthenticationEnabled, authenticated ->
            if (isAuthenticationEnabled) {
                authenticated.not()
            } else {
                false
            }
        }

    val secretState: StateFlow<SecretState> =
        combine(
            walletCoordinator.persistableWallet,
            isBackupComplete,
            isAuthenticationRequire,
        ) {
            persistableWallet: PersistableDarkfiWallet?,
            isBackupComplete: Boolean,
            isAuthenticationRequire: Boolean,
            ->
            when {
                isAuthenticationRequire -> SecretState.NeedAuthentication
                persistableWallet == null -> SecretState.None
                !isBackupComplete -> SecretState.NeedsBackup(persistableWallet)
                else -> SecretState.Ready(persistableWallet)
            }
        }.stateIn(
            viewModelScope,
            // Eagerly: splash keep-on-screen reads .value before Compose subscribes.
            SharingStarted.Eagerly,
            SecretState.Loading,
        )

    val spendingKey: StateFlow<DarkfiSpendKey?> =
        secretState
            .map {
                when (it) {
                    is SecretState.Ready -> DarkfiSpendKey(0)
                    else -> null
                }
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT.inWholeMilliseconds),
                null,
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    val walletSnapshot: StateFlow<WalletSnapshot?> =
        synchronizer
            .flatMapLatest { sync ->
                sync?.toWalletSnapshotFlow() ?: flowOf(null)
            }.throttle(1.seconds)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT.inWholeMilliseconds),
                null,
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    val transactionSnapshot: StateFlow<ImmutableList<DarkfiTransactionOverview>> =
        synchronizer
            .flatMapLatest { sync ->
                sync?.transactions?.map { list -> list.toPersistentList() } ?: flowOf(persistentListOf())
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT.inWholeMilliseconds),
                persistentListOf(),
            )

    val isBandit: StateFlow<Boolean> =
        flowOf(false).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT.inWholeMilliseconds),
            false,
        )

    private val addressRefreshTrigger =
        kotlinx.coroutines.flow
            .MutableSharedFlow<Unit>(replay = 1)
            .apply { tryEmit(Unit) }

    val addresses: StateFlow<DarkfiWalletAddresses?> =
        combine(synchronizer, addressRefreshTrigger) { sync, _ ->
            withContext(Dispatchers.IO) {
                sync?.walletAddresses()
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT.inWholeMilliseconds),
            null,
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val transactionHistoryState: StateFlow<TransactionHistorySyncState> =
        synchronizer
            .flatMapLatest { sync ->
                if (sync == null) {
                    flowOf(TransactionHistorySyncState.Loading)
                } else {
                    combine(sync.transactions, sync.status) { transactions, status ->
                        when (status) {
                            DarkfiSyncStatus.SYNCING -> {
                                TransactionHistorySyncState.Syncing(transactions.toPersistentList())
                            }

                            else -> {
                                TransactionHistorySyncState.Done(transactions.toPersistentList())
                            }
                        }
                    }
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT.inWholeMilliseconds),
                initialValue = TransactionHistorySyncState.Loading,
            )

    fun persistNewWallet() {
        val application = getApplication<Application>()
        _createWalletError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val network = darkfiNetworkFromPackage(application)
                val newWallet =
                    PersistableDarkfiWallet.create(
                        application,
                        network,
                        WalletInitMode.NewWallet,
                    )
                persistExistingWalletSuspend(newWallet)
            }.onFailure { e ->
                Twig.error(e) {
                    "Create wallet failed (rebuild ./scripts/build-darkfi-mobile-ffi-android.sh if UniFFI checksum mismatch)"
                }
                _createWalletError.value = e.message ?: "Create wallet failed"
            }
        }
    }

    fun persistExistingWallet(persistableWallet: PersistableDarkfiWallet) {
        viewModelScope.launch(Dispatchers.IO) {
            persistExistingWalletSuspend(persistableWallet)
        }
    }

    private suspend fun persistExistingWalletSuspend(persistableWallet: PersistableDarkfiWallet) {
        val application = getApplication<Application>()
        val preferenceProvider = EncryptedPreferenceSingleton.getInstance(application)
        persistWalletMutex.withLock {
            EncryptedPreferenceKeys.PERSISTABLE_DARKFI_WALLET.putValue(preferenceProvider, persistableWallet)
        }
    }

    fun updateDarkfiEndpoint(endpoint: DarkfiEndpoint): Boolean {
        val wallet = walletCoordinator.persistableWallet.value ?: return false
        when (val check = DarkfiEndpointNetworkGuard.validate(wallet.network, endpoint)) {
            is DarkfiEndpointNetworkGuard.Result.Mismatch -> {
                _endpointSaveError.value = DarkfiEndpointNetworkGuard.mismatchMessage(check)
                return false
            }

            DarkfiEndpointNetworkGuard.Result.Ok -> {
                _endpointSaveError.value = null
            }
        }
        val application = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            persistWalletMutex.withLock {
                val preferenceProvider = EncryptedPreferenceSingleton.getInstance(application)
                EncryptedPreferenceKeys.PERSISTABLE_DARKFI_WALLET.getValue(preferenceProvider)?.copyWithEndpoint(endpoint)?.let {
                    persistExistingWallet(it)
                }
            }
        }
        return true
    }

    fun persistBackupComplete() {
        val application = getApplication<Application>()
        viewModelScope.launch {
            val preferenceProvider = StandardPreferenceSingleton.getInstance(application)
            persistWalletMutex.withLock {
                StandardPreferenceKeys.IS_USER_BACKUP_COMPLETE.putValue(preferenceProvider, true)
            }
        }
    }

    fun rescanBlockchain() {
        viewModelScope.launch {
            walletCoordinator.rescanBlockchain()
        }
    }

    fun resetSdk() {
        walletCoordinator.resetSdk()
    }

    fun generateNewAddress() {
        viewModelScope.launch(Dispatchers.IO) {
            synchronizer.value?.generateNewAddress()
            addressRefreshTrigger.tryEmit(Unit)
        }
    }

    fun updateAuthenticationState(isUserAuthenticated: Boolean) {
        this.isUserAuthenticated.update { isUserAuthenticated }
    }
}

sealed class SecretState {
    data object Loading : SecretState()

    data object NeedAuthentication : SecretState()

    data object None : SecretState()

    class NeedsBackup(
        val persistableWallet: PersistableDarkfiWallet
    ) : SecretState()

    class Ready(
        val persistableWallet: PersistableDarkfiWallet
    ) : SecretState()
}

private fun DarkfiSynchronizer.toWalletSnapshotFlow() =
    com.nighthawkapps.lib.android.ui.common
        .combineNine(
            status,
            processorInfo,
            confirmedBalanceAtomic,
            progress,
            walletErrors,
            syncType,
            syncStatusMessage,
            syncTypeMessage,
            omrAvailable,
        ) {
            status,
            processorInfo,
            confirmedBalanceAtomic,
            progress,
            walletErrors,
            syncType,
            syncStatusMessage,
            syncTypeMessage,
            omrAvailable,
            ->
            WalletSnapshot(
                status = status,
                processorInfo = processorInfo,
                confirmedBalanceAtomic = confirmedBalanceAtomic,
                progress = progress,
                walletError = walletErrors,
                syncType = syncType,
                syncStatusMessage = syncStatusMessage,
                syncTypeMessage = syncTypeMessage,
                omrAvailable = omrAvailable,
            )
        }.combine(syncMethod) { snapshot, method ->
            snapshot.copy(syncMethod = method)
        }.combine(fallbackReason) { snapshot, reason ->
            snapshot.copy(fallbackReason = reason)
        }.combine(fallbackUserMessage) { snapshot, message ->
            snapshot.copy(fallbackUserMessage = message)
        }
