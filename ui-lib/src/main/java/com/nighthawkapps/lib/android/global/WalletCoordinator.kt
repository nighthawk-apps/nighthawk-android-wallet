package com.nighthawkapps.lib.android.global

import android.content.Context
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiWalletCoordinator
import com.nighthawkapps.lib.android.spackle.LazyWithArgument
import com.nighthawkapps.lib.android.ui.preference.EncryptedPreferenceKeys
import com.nighthawkapps.lib.android.ui.preference.EncryptedPreferenceSingleton
import com.nighthawkapps.lib.android.ui.preference.StandardPreferenceKeys
import com.nighthawkapps.lib.android.ui.preference.StandardPreferenceSingleton
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

object AppWalletCoordinator {
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
            DarkfiWalletCoordinator.create(it, persistableWallet, backupComplete)
        }

    fun get(context: Context): DarkfiWalletCoordinator = lazyCoordinator.getInstance(context)
}
