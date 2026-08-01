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
 * Secrets are stored with Secure Datastore ([DarkfiChatSecureStore]).
 */
class DarkfiChatIdentity(
    appContext: Context
) {
    private val app = appContext.applicationContext

    fun hasIdentity(): Boolean = DarkfiChatSecureStore.getString(app, DarkfiChatSecureStore.KEY_SECRET_PHRASE)?.isNotBlank() == true

    fun publicIdHex(): String? = DarkfiChatSecureStore.getString(app, DarkfiChatSecureStore.KEY_PUBLIC_ID)?.takeIf { it.isNotBlank() }

    /** Words exactly as stored (lowercase BIP-39). */
    fun secretPhraseWords(): List<String>? {
        val raw =
            DarkfiChatSecureStore.getString(app, DarkfiChatSecureStore.KEY_SECRET_PHRASE)?.trim().orEmpty()
        if (raw.isBlank()) return null
        return raw.split(Regex("\\s+")).filter { it.isNotBlank() }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    suspend fun generateNew(assetManager: AssetManager): Boolean {
        val words = generateBip39ChatMnemonic()
        val normalized = words.map { it.lowercase() }
        val entropy = decodeChatEntropy(normalized)?.toUByteArray()?.toByteArray() ?: return false
        persist(normalized, entropy)
        return true
    }

    @OptIn(ExperimentalUnsignedTypes::class)
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
        DarkfiChatSecureStore.remove(
            app,
            DarkfiChatSecureStore.KEY_SECRET_PHRASE,
            DarkfiChatSecureStore.KEY_PUBLIC_ID
        )
    }

    private fun persist(
        words: List<String>,
        entropy: ByteArray
    ) {
        val pub = DarkfiChatIdentityCrypto.publicIdHex(entropy)
        DarkfiChatSecureStore.putString(app, DarkfiChatSecureStore.KEY_SECRET_PHRASE, words.joinToString(" "))
        DarkfiChatSecureStore.putString(app, DarkfiChatSecureStore.KEY_PUBLIC_ID, pub)
    }
}
