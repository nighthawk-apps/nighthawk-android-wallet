package com.nighthawkapps.lib.android.sdk.wallet

import android.content.Context

/** Creates wallet synchronizers (native when UniFFI probe succeeds, else stub). */
internal object DarkfiSynchronizerFactory {
    fun create(
        wallet: PersistableDarkfiWallet,
        applicationContext: Context,
        useNativeSynchronizer: Boolean,
    ): DarkfiSynchronizer =
        if (useNativeSynchronizer) {
            runCatching { NativeDarkfiSynchronizer(wallet, applicationContext) }
                .getOrElse { StubDarkfiSynchronizer(wallet, applicationContext) }
        } else {
            StubDarkfiSynchronizer(wallet, applicationContext)
        }
}
