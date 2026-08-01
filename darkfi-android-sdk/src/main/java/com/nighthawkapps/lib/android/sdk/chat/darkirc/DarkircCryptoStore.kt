package com.nighthawkapps.lib.android.sdk.chat.darkirc

import android.content.Context
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatSecureStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Encrypted persistence for upstream-style `[channel.*]` / `[contact.*]` TOML sections.
 * E2E crypto keys for chat (ChaChaBox); Kotlin stores them for the UI / prefs.
 */
internal object DarkircCryptoStore {
    private const val KEY_CRYPTO_BUNDLE = "darkirc_crypto_bundle_json"

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    fun load(context: Context): DarkircCryptoBundle {
        val raw =
            DarkfiChatSecureStore
                .getString(context.applicationContext, KEY_CRYPTO_BUNDLE)
                ?: return DarkircCryptoBundle()
        return runCatching { json.decodeFromString<DarkircCryptoBundle>(raw) }
            .getOrDefault(DarkircCryptoBundle())
    }

    fun save(
        context: Context,
        bundle: DarkircCryptoBundle,
    ) {
        DarkfiChatSecureStore
            .putString(context.applicationContext, KEY_CRYPTO_BUNDLE, json.encodeToString(bundle))
    }

    fun upsertChannel(
        context: Context,
        config: DarkircChannelCryptoConfig,
    ) {
        val current = load(context)
        val without = current.channels.filterNot { it.channel == config.channel }
        save(context, current.copy(channels = without + config))
    }

    fun upsertContact(
        context: Context,
        config: DarkircContactCryptoConfig,
    ) {
        val current = load(context)
        val without = current.contacts.filterNot { it.nick == config.nick }
        save(context, current.copy(contacts = without + config))
    }

    fun removeContact(
        context: Context,
        nick: String,
    ) {
        val current = load(context)
        save(context, current.copy(contacts = current.contacts.filterNot { it.nick == nick }))
    }
}
