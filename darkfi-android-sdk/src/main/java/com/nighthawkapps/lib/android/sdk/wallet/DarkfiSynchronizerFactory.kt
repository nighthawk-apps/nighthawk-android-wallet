package com.nighthawkapps.lib.android.sdk.wallet

import android.content.Context

/** Creates wallet synchronizers (native when UniFFI probe succeeds, else stub). */
object DarkfiSynchronizerFactory {
    fun create(
        wallet: PersistableDarkfiWallet,
        applicationContext: Context,
        useNativeSynchronizer: Boolean,
    ): DarkfiSynchronizer =
        if (useNativeSynchronizer) {
            runCatching { NativeDarkfiSynchronizer(wallet, applicationContext) }
                .onFailure { android.util.Log.e("TEST_WALLET", "NativeDarkfiSynchronizer failed to init", it) }
                .getOrElse { e ->
                    StubDarkfiSynchronizer(
                        wallet,
                        applicationContext,
                        nativeInitError = e.message ?: e.toString(),
                    )
                }
        } else {
            StubDarkfiSynchronizer(wallet, applicationContext)
        }
}
