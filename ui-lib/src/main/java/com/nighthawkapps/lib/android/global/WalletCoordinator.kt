package com.nighthawkapps.lib.android.global

import android.content.Context
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiWalletCoordinator
import com.nighthawkapps.lib.android.spackle.LazyWithArgument
import com.nighthawkapps.lib.android.ui.preference.EncryptedPreferenceKeys
import com.nighthawkapps.lib.android.ui.preference.EncryptedPreferenceSingleton
import com.nighthawkapps.lib.android.ui.preference.StandardPreferenceKeys
import com.nighthawkapps.lib.android.ui.preference.StandardPreferenceSingleton
import com.nighthawkapps.lib.android.ui.security.WalletPinSecureStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

object AppWalletCoordinator {
    private val _isUserAuthenticated = MutableStateFlow(false)
    val isUserAuthenticated: StateFlow<Boolean> = _isUserAuthenticated.asStateFlow()

    private val _isBackgroundSyncActive = MutableStateFlow(false)

    fun setUserAuthenticated(authenticated: Boolean) {
        _isUserAuthenticated.value = authenticated
    }

    fun setBackgroundSyncActive(active: Boolean) {
        _isBackgroundSyncActive.value = active
    }

    private val lazyCoordinator =
        LazyWithArgument<Context, DarkfiWalletCoordinator> {
            val persistableWallet =
                flow {
                    val encryptedPreferenceProvider = EncryptedPreferenceSingleton.getInstance(it)
                    emitAll(EncryptedPreferenceKeys.PERSISTABLE_DARKFI_WALLET.observe(encryptedPreferenceProvider))
                }
            val backupComplete =
                flow {
                    val preferenceProvider = StandardPreferenceSingleton.getInstance(it)
                    emitAll(StandardPreferenceKeys.IS_USER_BACKUP_COMPLETE.observe(preferenceProvider))
                }
            val pinConfigured = WalletPinSecureStore.isConfiguredFlow(it)
            val synchronizerAllowed =
                combine(
                    backupComplete,
                    pinConfigured,
                    _isUserAuthenticated,
                    _isBackgroundSyncActive,
                ) { backedUp, hasPin, authenticated, bgSyncActive ->
                    backedUp && (!hasPin || authenticated || bgSyncActive)
                }
            DarkfiWalletCoordinator.create(it, persistableWallet, synchronizerAllowed)
        }

    fun get(context: Context): DarkfiWalletCoordinator = lazyCoordinator.getInstance(context)
}
