package com.nighthawkapps.lib.android.sdk.chat.darkirc

import android.content.Context
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatConnectionBridge
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatSecureStore
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.clearDmContacts
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.registerDmContact
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.unregisterDmContact
import java.io.File

object DarkircCryptoManager {
    private const val KEY_DM_SHARE_PUBLIC = "dm_share_public_b58"
    private const val KEY_DM_SHARE_SECRET = "dm_share_secret_b58"
    private const val LEGACY_BUNDLE_FILE = "darkirc_crypto_bundle.json"

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
        val app = context.applicationContext
        val previous = loadContacts(app).firstOrNull { it.nick == config.nick }
        DarkircCryptoStore.upsertContact(app, config)
        if (!registerSingleContactInNative(config)) {
            if (previous != null) {
                DarkircCryptoStore.upsertContact(app, previous)
                registerSingleContactInNative(previous)
            } else {
                DarkircCryptoStore.removeContact(app, config.nick)
            }
            throw IllegalStateException("DM contact was not registered")
        }
        applyAndRestartEmbeddedDaemon(app)
    }

    fun removeContact(
        context: Context,
        nick: String,
    ) {
        val app = context.applicationContext
        DarkircCryptoStore.removeContact(app, nick)
        runCatching { unregisterDmContact(nick) }
    }

    /**
     * Scrubs any legacy plaintext `darkirc_crypto_bundle.json` on disk and pushes all
     * encrypted contacts into `darkirc_daemon`'s zeroized in-memory `DM_CONTACTS` map.
     */
    fun syncContactsToNative(context: Context) {
        val app = context.applicationContext
        scrubLegacyBundleFiles(app)
        val contacts = loadContacts(app)
        clearDmContacts()
        val failed = contacts.count { !registerSingleContactInNative(it) }
        if (failed > 0) {
            android.util.Log.e("DarkircCrypto", "DM contact registration failed for $failed contact(s)")
        }
    }

    private fun registerSingleContactInNative(config: DarkircContactCryptoConfig): Boolean {
        if (config.nick.isBlank()) return false
        val mySecret = DarkircBs58.decode32OrNull(config.myDmChachaSecretBase58) ?: return false
        val theirPublic = DarkircBs58.decode32OrNull(config.dmChachaPublicBase58)
        if (theirPublic == null) {
            mySecret.fill(0)
            return false
        }
        return try {
            registerDmContact(
                nick = config.nick,
                mySecret = mySecret.map { it.toUByte() },
                theirPublic = theirPublic.map { it.toUByte() },
            )
            true
        } catch (_: Exception) {
            android.util.Log.e("DarkircCrypto", "DM contact registration failed")
            false
        } finally {
            mySecret.fill(0)
        }
    }

    fun scrubLegacyBundleFiles(context: Context) {
        val app = context.applicationContext
        val candidates =
            listOf(
                File(File(File(app.filesDir, "darkirc"), "darkirc_db"), LEGACY_BUNDLE_FILE),
                File(File(app.filesDir, "darkirc"), LEGACY_BUNDLE_FILE),
                File(app.filesDir, LEGACY_BUNDLE_FILE),
            )
        candidates.forEach { file ->
            runCatching {
                if (file.isFile) {
                    val len = file.length().toInt().coerceAtLeast(0)
                    if (len > 0) {
                        file.writeBytes(ByteArray(len))
                    }
                    file.delete()
                }
            }
        }
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
