package com.nighthawkapps.lib.android.sdk.chat.darkirc

import android.content.Context
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatSecureStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Encrypted persistence for upstream-style `[channel.*]` / `[contact.*]` TOML sections.
 * E2E crypto runs inside embedded **`darkirc`** (ChaChaBox); Kotlin only stores keys and emits TOML.
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
                .sharedPreferences(context.applicationContext)
                .getString(KEY_CRYPTO_BUNDLE, null)
                ?: return DarkircCryptoBundle()
        return runCatching { json.decodeFromString<DarkircCryptoBundle>(raw) }
            .getOrDefault(DarkircCryptoBundle())
    }

    fun save(
        context: Context,
        bundle: DarkircCryptoBundle,
    ) {
        DarkfiChatSecureStore
            .sharedPreferences(context.applicationContext)
            .edit()
            .putString(KEY_CRYPTO_BUNDLE, json.encodeToString(bundle))
            .apply()
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
