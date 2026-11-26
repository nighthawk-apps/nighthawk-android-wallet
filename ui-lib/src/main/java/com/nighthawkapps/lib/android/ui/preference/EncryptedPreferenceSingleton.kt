package com.nighthawkapps.lib.android.ui.preference

import android.content.Context
import android.util.Log
import com.nighthawkapps.lib.android.preference.AndroidPreferenceProvider
import com.nighthawkapps.lib.android.preference.api.PreferenceProvider
import com.nighthawkapps.lib.android.preference.model.entry.PreferenceKey
import com.nighthawkapps.lib.android.spackle.SuspendingLazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object EncryptedPreferenceSingleton {
    private const val PREF_FILENAME = "com.nighthawkapps.lib.android.encrypted"
    private const val TAG = "EncryptedPrefs"
    private const val MIGRATION_FLAG = "encrypted_prefs_migrated_v1"

    private val lazy =
        SuspendingLazy<Context, PreferenceProvider> { context ->
            // Emulator-only escape hatch — never newStandard in release.
            val allowPlaintext =
                try {
                    val debug =
                        (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
                    debug &&
                        context
                            .getSharedPreferences("nighthawk_debug", Context.MODE_PRIVATE)
                            .getBoolean("allow_plaintext_prefs", false)
                } catch (_: Exception) {
                    false
                }

            if (allowPlaintext) {
                Log.w(TAG, "DEBUG: using plaintext SharedPreferences (allow_plaintext_prefs)")
                return@SuspendingLazy AndroidPreferenceProvider.newStandard(context, PREF_FILENAME)
            }

            val encrypted = AndroidPreferenceProvider.newEncrypted(context, PREF_FILENAME)
            migratePlaintextStringsIfNeeded(context, encrypted)
            encrypted
        }

    suspend fun getInstance(context: Context) = lazy.getInstance(context)

    /**
     * Copy string keys from the old plaintext SharedPreferences file (emulator
     * workaround) into the encrypted DataStore, then clear plaintext.
     */
    private suspend fun migratePlaintextStringsIfNeeded(
        context: Context,
        encrypted: PreferenceProvider,
    ) = withContext(Dispatchers.IO) {
        val plain = context.getSharedPreferences(PREF_FILENAME, Context.MODE_PRIVATE)
        if (plain.getBoolean(MIGRATION_FLAG, false)) return@withContext
        val entries = plain.all
        if (entries.isEmpty()) {
            plain.edit().putBoolean(MIGRATION_FLAG, true).apply()
            return@withContext
        }
        Log.i(TAG, "Migrating plaintext preferences into the encrypted store")
        var failed = false
        for ((name, value) in entries) {
            if (name == MIGRATION_FLAG) continue
            val serializedValue =
                when (value) {
                    is String -> {
                        value
                    }

                    is Boolean, is Int, is Long, is Float, is Double -> {
                        value.toString()
                    }

                    else -> {
                        Log.w(TAG, "Skipping an unsupported plaintext preference value")
                        continue
                    }
                }
            try {
                encrypted.putString(PreferenceKey(name), serializedValue)
                if (encrypted.getString(PreferenceKey(name)) != serializedValue) {
                    failed = true
                    Log.e(TAG, "Encrypted read-back mismatch; keeping plaintext")
                }
            } catch (_: Throwable) {
                failed = true
                Log.w(TAG, "Skipped migrating one preference")
            }
        }
        if (failed) {
            // Leave the plaintext file in place so a later launch can retry.
            // Release still uses the keystore store — never newStandard.
            return@withContext
        }
        plain
            .edit()
            .clear()
            .putBoolean(MIGRATION_FLAG, true)
            .commit()
    }
}
