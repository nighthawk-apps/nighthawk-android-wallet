package com.nighthawkapps.lib.android.sdk.chat

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.nighthawkapps.lib.android.spackle.Twig

/**
 * AES-backed prefs for chat secrets (identity phrase, public id fingerprint, IRC PASS).
 * Migrates once from legacy plaintext files.
 *
 * [EncryptedSharedPreferences] is not documented as thread-safe; all access is guarded by [initLock].
 */
internal object DarkfiChatSecureStore {
    const val KEY_SECRET_PHRASE: String = "secret_phrase_words"
    const val KEY_PUBLIC_ID: String = "public_id_sha_hex"

    /** Must match legacy key in [DarkfiChatPreferences] transport file for migration. */
    const val KEY_IRC_PASSWORD: String = "darkfi_irc_password"

    private const val ENC_PREFS_NAME: String = "darkfi_chat_encrypted"
    private const val LEGACY_IDENTITY_PREFS: String = "darkfi_chat_identity"
    private const val TRANSPORT_PREFS: String = "darkfi_chat_transport"

    private val initLock = Any()

    @Volatile private var instance: SharedPreferences? = null

    fun sharedPreferences(context: Context): SharedPreferences {
        instance?.let { return it }
        synchronized(initLock) {
            instance?.let { return it }
            val app = context.applicationContext
            val prefs =
                try {
                    val masterKey =
                        MasterKey
                            .Builder(app)
                            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                            .build()
                    EncryptedSharedPreferences.create(
                        app,
                        ENC_PREFS_NAME,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                    )
                } catch (e: Exception) {
                    Twig.error(e) {
                        "EncryptedSharedPreferences unavailable; failing securely"
                    }
                    throw IllegalStateException("Failed to initialize secure preferences", e)
                }
            migrateLegacyPlaintext(app, prefs)
            instance = prefs
            return prefs
        }
    }

    private fun migrateLegacyPlaintext(
        app: Context,
        enc: SharedPreferences
    ) {
        val legacyIdentity = app.getSharedPreferences(LEGACY_IDENTITY_PREFS, Context.MODE_PRIVATE)
        val phrase = legacyIdentity.getString(KEY_SECRET_PHRASE, null)
        val pub = legacyIdentity.getString(KEY_PUBLIC_ID, null)
        if (!phrase.isNullOrBlank()) {
            if (enc.getString(KEY_SECRET_PHRASE, null).isNullOrBlank()) {
                enc
                    .edit()
                    .putString(KEY_SECRET_PHRASE, phrase)
                    .putString(KEY_PUBLIC_ID, pub.orEmpty())
                    .apply()
            }
            legacyIdentity.edit().clear().apply()
        }

        val transport = app.getSharedPreferences(TRANSPORT_PREFS, Context.MODE_PRIVATE)
        val plainPass = transport.getString(KEY_IRC_PASSWORD, null)
        if (!plainPass.isNullOrBlank()) {
            if (enc.getString(KEY_IRC_PASSWORD, null).isNullOrBlank()) {
                enc.edit().putString(KEY_IRC_PASSWORD, plainPass).apply()
            }
            transport.edit().remove(KEY_IRC_PASSWORD).apply()
        }
    }
}
