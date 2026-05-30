@file:Suppress("TooGenericExceptionCaught", "SwallowedException", "ReturnCount")

package com.nighthawkapps.lib.android.sdk.chat

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

    fun encryptMessageIfPossible(
        context: android.content.Context,
        targetNickOrChannel: String,
        plaintext: String
    ): String {
        if (targetNickOrChannel.startsWith("#")) {
            return plaintext // Channel crypto not fully supported in this snippet yet
        }
        val contacts =
            com.nighthawkapps.lib.android.sdk.chat.darkirc.DarkircCryptoManager
                .loadContacts(context)
        val contact = contacts.find { it.nick == targetNickOrChannel } ?: return plaintext
        val mySecret =
            com.nighthawkapps.lib.android.sdk.chat.darkirc.DarkircBs58
                .decode32OrNull(contact.myDmChachaSecretBase58) ?: return plaintext
        val theirPublic =
            com.nighthawkapps.lib.android.sdk.chat.darkirc.DarkircBs58
                .decode32OrNull(contact.dmChachaPublicBase58) ?: return plaintext
        return try {
            encryptDm(mySecret, theirPublic, plaintext)
        } catch (e: Exception) {
            com.nighthawkapps.lib.android.spackle.Twig
                .error(e) { "Failed to encrypt DM" }
            plaintext
        }
    }

    fun decryptMessageIfPossible(
        context: android.content.Context,
        msg: ChatChannelMessage
    ): ChatChannelMessage {
        if (msg.channel.startsWith("#")) {
            return msg // Channel crypto not fully supported in this snippet yet
        }
        // msg.channel is our nick, msg.nick is the sender's nick. We lookup contact by sender's nick.
        val contacts =
            com.nighthawkapps.lib.android.sdk.chat.darkirc.DarkircCryptoManager
                .loadContacts(context)
        val contact = contacts.find { it.nick == msg.nick } ?: return msg
        val mySecret =
            com.nighthawkapps.lib.android.sdk.chat.darkirc.DarkircBs58
                .decode32OrNull(contact.myDmChachaSecretBase58) ?: return msg
        val theirPublic =
            com.nighthawkapps.lib.android.sdk.chat.darkirc.DarkircBs58
                .decode32OrNull(contact.dmChachaPublicBase58) ?: return msg
        return try {
            val decrypted = decryptDm(mySecret, theirPublic, msg.text)
            msg.copy(text = decrypted)
        } catch (e: Exception) {
            // Either not encrypted or bad key, return original msg
            msg
        }
    }
}
