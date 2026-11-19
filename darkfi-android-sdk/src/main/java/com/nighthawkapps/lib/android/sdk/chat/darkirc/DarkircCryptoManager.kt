package com.nighthawkapps.lib.android.sdk.chat.darkirc

import android.content.Context
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatConnectionBridge
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatSecureStore

object DarkircCryptoManager {
    private const val KEY_DM_SHARE_PUBLIC = "dm_share_public_b58"
    private const val KEY_DM_SHARE_SECRET = "dm_share_secret_b58"

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

    /** Keypair whose public half was shown or posted. Reused so add-contact does not mint another. */
    fun loadShareKeypair(context: Context): DarkircCliKeygen.DmKeypair? {
        val app = context.applicationContext
        val publicKey = DarkfiChatSecureStore.getString(app, KEY_DM_SHARE_PUBLIC) ?: return null
        val secret = DarkfiChatSecureStore.getString(app, KEY_DM_SHARE_SECRET) ?: return null
        if (!DarkircBs58.isValidSecret32(publicKey) || !DarkircBs58.isValidSecret32(secret)) {
            return null
        }
        return DarkircCliKeygen.DmKeypair(
            myDmChachaPublicBase58 = publicKey,
            myDmChachaSecretBase58 = secret,
        )
    }

    fun saveShareKeypair(
        context: Context,
        keypair: DarkircCliKeygen.DmKeypair,
    ) {
        val app = context.applicationContext
        DarkfiChatSecureStore.putString(app, KEY_DM_SHARE_PUBLIC, keypair.myDmChachaPublicBase58)
        DarkfiChatSecureStore.putString(app, KEY_DM_SHARE_SECRET, keypair.myDmChachaSecretBase58)
    }

    fun ensureShareKeypair(context: Context): DarkircCliKeygen.DmKeypair? {
        loadShareKeypair(context)?.let { return it }
        val created = generateDmKeypair(context) ?: return null
        saveShareKeypair(context, created)
        return created
    }

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
