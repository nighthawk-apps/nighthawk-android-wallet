package com.nighthawkapps.lib.android.sdk.chat.darkirc

import android.content.Context
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatConnectionBridge

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
        applyAndRestartEmbeddedDaemon(context)
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

    fun generateChannelSecret(context: Context): String? = DarkircCliKeygen.genChannelSecret(context.applicationContext)

    fun generateDmKeypair(context: Context): DarkircCliKeygen.DmKeypair? = DarkircCliKeygen.genChachaKeypair(context.applicationContext)

    /** UniFFI darkirc is always linked into the app binary. */
    fun hasBundledDarkirc(context: Context): Boolean = true

    /**
     * Persist contacts then reconnect the EventGraph session. Does not bounce
     * the embedded daemon process (no stop_darkirc); the native node picks up
     * contact state on the next session.
     */
    fun applyAndRestartEmbeddedDaemon(context: Context): Boolean {
        DarkfiChatConnectionBridge.requestReconnect()
        return true
    }
}
