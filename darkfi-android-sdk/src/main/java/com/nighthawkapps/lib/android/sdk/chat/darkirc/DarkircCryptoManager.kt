@file:Suppress("TooManyFunctions", "MaxLineLength")

package com.nighthawkapps.lib.android.sdk.chat.darkirc

import android.content.Context

/**
 * Public API for DarkIRC **ChaChaBox** channel/contact keys.
 *
 * Keys are persisted in encrypted prefs and merged into embedded **`darkirc_config.toml`**
 * on [applyAndRestartEmbeddedDaemon]. Encryption/decryption runs inside **`darkirc`**, not Kotlin.
 */
object DarkircCryptoManager {
    fun loadBundle(context: Context): DarkircCryptoBundle = DarkircCryptoStore.load(context.applicationContext)

    fun loadChannels(context: Context): List<DarkircChannelCryptoConfig> = loadBundle(context).channels

    fun loadContacts(context: Context): List<DarkircContactCryptoConfig> = loadBundle(context).contacts

    fun saveChannel(
        context: Context,
        config: DarkircChannelCryptoConfig,
    ) {
        DarkircCryptoStore.upsertChannel(context.applicationContext, config)
    }

    fun saveContact(
        context: Context,
        config: DarkircContactCryptoConfig,
    ) {
        DarkircCryptoStore.upsertContact(context.applicationContext, config)
    }

    fun removeContact(
        context: Context,
        nick: String,
    ) {
        DarkircCryptoStore.removeContact(context.applicationContext, nick)
    }

    fun removeChannel(
        context: Context,
        channel: String,
    ) {
        val app = context.applicationContext
        val current = DarkircCryptoStore.load(app)
        DarkircCryptoStore.save(
            app,
            current.copy(channels = current.channels.filterNot { it.channel == channel }),
        )
    }

    /** Requires packaged **`darkirc_exec`**. */
    fun generateChannelSecret(context: Context): String? = DarkircCliKeygen.genChannelSecret(context.applicationContext)

    /** Requires packaged **`darkirc_exec`**. */
    fun generateDmKeypair(context: Context): DarkircCliKeygen.DmKeypair? = DarkircCliKeygen.genChachaKeypair(context.applicationContext)

    /** Requires packaged **`darkirc_exec`**. */
    fun publicKeyFromSecret(
        context: Context,
        secretBase58: String,
    ): String? = DarkircCliKeygen.publicKeyFromSecret(context.applicationContext, secretBase58)

    fun hasBundledDarkirc(context: Context): Boolean = DarkircEmbeddedRunner.hasBundledBinary(context.applicationContext)

    /**
     * Rewrites `darkirc_config.toml` with current crypto bundle and restarts embedded daemon if needed.
     */
    fun applyAndRestartEmbeddedDaemon(context: Context): Boolean =
        DarkircEmbeddedRunner.syncEmbeddedIrcListenConfigAndEnsureRunning(context.applicationContext)
}
