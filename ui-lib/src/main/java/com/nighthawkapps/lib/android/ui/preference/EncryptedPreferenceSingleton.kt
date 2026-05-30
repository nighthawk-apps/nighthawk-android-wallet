package com.nighthawkapps.lib.android.ui.preference

import android.content.Context
import com.nighthawkapps.lib.android.preference.AndroidPreferenceProvider
import com.nighthawkapps.lib.android.preference.api.PreferenceProvider
import com.nighthawkapps.lib.android.spackle.SuspendingLazy

object EncryptedPreferenceSingleton {
    private const val PREF_FILENAME = "com.nighthawkapps.lib.android.encrypted"

    private val lazy =
        SuspendingLazy<Context, PreferenceProvider> {
            AndroidPreferenceProvider.newEncrypted(it, PREF_FILENAME)
        }

    suspend fun getInstance(context: Context) = lazy.getInstance(context)
}
