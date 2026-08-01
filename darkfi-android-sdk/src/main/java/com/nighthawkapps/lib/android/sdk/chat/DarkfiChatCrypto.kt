@file:Suppress("TooGenericExceptionCaught", "SwallowedException", "ReturnCount")

package com.nighthawkapps.lib.android.sdk.chat

import android.content.Context
import com.nighthawkapps.lib.android.sdk.chat.darkirc.DarkircBs58
import com.nighthawkapps.lib.android.sdk.chat.darkirc.DarkircCryptoManager
import com.nighthawkapps.lib.android.spackle.Twig
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.chachaDecryptDm
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.chachaEncryptDm

/**
 * End-to-end encryption for DarkFi chat direct messages and channels.
 * Uses the native Rust ChaCha primitives from darkfi-mobile-ffi.
 */
object DarkfiChatCrypto {
    /**
     * Encrypts a direct message payload using ChaCha20Poly1305.
     * @param mySecret The sender's secret key bytes.
     * @param theirPublic The recipient's public key bytes.
     * @param plaintext The message to encrypt.
     * @return Base58 encoded ciphertext with prepended nonce.
     */
    fun encryptDm(
        mySecret: ByteArray,
        theirPublic: ByteArray,
        plaintext: String
    ): String = chachaEncryptDm(mySecret.map { it.toUByte() }, theirPublic.map { it.toUByte() }, plaintext)

    /**
     * Decrypts a direct message payload using ChaCha20Poly1305.
     * @param mySecret The recipient's secret key bytes.
     * @param theirPublic The sender's public key bytes.
     * @param ciphertextB58 Base58 encoded ciphertext with prepended nonce.
     * @return The decrypted plaintext.
     */
    fun decryptDm(
        mySecret: ByteArray,
        theirPublic: ByteArray,
        ciphertextB58: String
    ): String = chachaDecryptDm(mySecret.map { it.toUByte() }, theirPublic.map { it.toUByte() }, ciphertextB58)

    /**
     * When [delegateWireCryptoToDaemon] is true (embedded darkirc), the daemon encrypts on the IRC wire.
     */
    fun encryptMessageIfPossible(
        context: Context,
        targetContactLabelOrChannel: String,
        plaintext: String,
        delegateWireCryptoToDaemon: Boolean = false,
    ): String {
        if (delegateWireCryptoToDaemon || targetContactLabelOrChannel.startsWith("#")) {
            return plaintext
        }
        val contacts = DarkircCryptoManager.loadContacts(context)
        val contact = contacts.find { it.nick == targetContactLabelOrChannel } ?: return plaintext
        val mySecret = DarkircBs58.decode32OrNull(contact.myDmChachaSecretBase58) ?: return plaintext
        val theirPublic = DarkircBs58.decode32OrNull(contact.dmChachaPublicBase58) ?: return plaintext
        return try {
            encryptDm(mySecret, theirPublic, plaintext)
        } catch (e: Exception) {
            Twig.error(e) { "Failed to encrypt DM" }
            plaintext
        }
    }

    fun decryptMessageIfPossible(
        context: Context,
        msg: ChatChannelMessage,
        delegateWireCryptoToDaemon: Boolean = false,
    ): ChatChannelMessage {
        if (delegateWireCryptoToDaemon || msg.channel.startsWith("#")) {
            return msg
        }
        // DM thread key is the upstream contact label (TOML [contact."label"]), in msg.channel after daemon decrypt.
        val contacts = DarkircCryptoManager.loadContacts(context)
        val contact = contacts.find { it.nick == msg.channel } ?: return msg
        val mySecret = DarkircBs58.decode32OrNull(contact.myDmChachaSecretBase58) ?: return msg
        val theirPublic = DarkircBs58.decode32OrNull(contact.dmChachaPublicBase58) ?: return msg
        return try {
            val decrypted = decryptDm(mySecret, theirPublic, msg.text)
            msg.copy(text = decrypted)
        } catch (e: Exception) {
            // Either not encrypted or bad key, return original msg
            msg
        }
    }
}
