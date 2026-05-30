@file:Suppress("UnusedParameter")

package com.nighthawkapps.lib.android.sdk.chat

import android.content.Context
import android.content.res.AssetManager
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.decodeChatEntropy
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.generateBip39ChatMnemonic

/**
 * Chat-local cryptographic identity (separate from the wallet seed): a BIP-39–compatible **12-word**
 * secret phrase and a shareable **public id** (hex fingerprint).
 *
 * Secrets are stored with [EncryptedSharedPreferences] ([DarkfiChatSecureStore]); plaintext legacy data is migrated on first access.
 */
class DarkfiChatIdentity(
    appContext: Context
) {
    private val app = appContext.applicationContext

    private fun prefs() = DarkfiChatSecureStore.sharedPreferences(app)

    fun hasIdentity(): Boolean = prefs().getString(DarkfiChatSecureStore.KEY_SECRET_PHRASE, null)?.isNotBlank() == true

    fun publicIdHex(): String? = prefs().getString(DarkfiChatSecureStore.KEY_PUBLIC_ID, null)?.takeIf { it.isNotBlank() }

    /** Words exactly as stored (lowercase BIP-39). */
    fun secretPhraseWords(): List<String>? {
        val raw =
            prefs().getString(DarkfiChatSecureStore.KEY_SECRET_PHRASE, null)?.trim().orEmpty()
        if (raw.isBlank()) return null
        return raw.split(Regex("\\s+")).filter { it.isNotBlank() }
    }

    suspend fun generateNew(assetManager: AssetManager): Boolean {
        val words = generateBip39ChatMnemonic()
        val normalized = words.map { it.lowercase() }
        val entropy = decodeChatEntropy(normalized)?.toUByteArray()?.toByteArray() ?: return false
        persist(normalized, entropy)
        return true
    }

    suspend fun restoreFromPhraseText(
        raw: String,
        assetManager: AssetManager
    ): Boolean {
        val words =
            raw
                .trim()
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .map { it.lowercase() }
        val entropy = decodeChatEntropy(words)?.toUByteArray()?.toByteArray() ?: return false
        persist(words, entropy)
        return true
    }

    fun clear() {
        prefs()
            .edit()
            .remove(DarkfiChatSecureStore.KEY_SECRET_PHRASE)
            .remove(DarkfiChatSecureStore.KEY_PUBLIC_ID)
            .apply()
    }

    private fun persist(
        words: List<String>,
        entropy: ByteArray
    ) {
        val pub = DarkfiChatIdentityCrypto.publicIdHex(entropy)
        prefs()
            .edit()
            .putString(DarkfiChatSecureStore.KEY_SECRET_PHRASE, words.joinToString(" "))
            .putString(DarkfiChatSecureStore.KEY_PUBLIC_ID, pub)
            .apply()
    }
}
