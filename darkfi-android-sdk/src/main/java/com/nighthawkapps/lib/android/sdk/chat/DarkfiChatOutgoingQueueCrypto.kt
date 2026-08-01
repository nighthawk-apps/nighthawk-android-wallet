package com.nighthawkapps.lib.android.sdk.chat

import android.content.Context
import com.nighthawkapps.lib.android.sdk.util.SecureDataStoreSerializer
import com.nighthawkapps.lib.android.spackle.Twig
import java.io.File

/**
 * AES-GCM file encryption for the offline chat outgoing queue.
 */
internal class DarkfiChatOutgoingQueueCrypto(
    private val context: Context,
    internal val queueFile: File,
) {
    private val aead by lazy {
        SecureDataStoreSerializer.createAead(context.applicationContext, "darkfi_chat_queue")
    }

    fun readText(): String? {
        if (!queueFile.exists()) return null
        return runCatching {
            val encryptedBytes = queueFile.readBytes()
            if (encryptedBytes.isEmpty()) return null
            val decryptedBytes = aead.decrypt(encryptedBytes, null)
            decryptedBytes.decodeToString()
        }.getOrElse { error ->
            Twig.warn(error) { "Failed to read encrypted chat queue; treating as empty" }
            null
        }
    }

    fun writeText(text: String) {
        val parent = queueFile.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        runCatching {
            val encryptedBytes = aead.encrypt(text.encodeToByteArray(), null)
            queueFile.writeBytes(encryptedBytes)
        }
    }

    fun delete() {
        runCatching { queueFile.delete() }
    }

    companion object {
        private const val ENC_FILE_NAME = "darkfi_chat_outgoing_queue.enc"

        fun create(context: Context): DarkfiChatOutgoingQueueCrypto {
            val app = context.applicationContext
            val encFile = File(app.filesDir, ENC_FILE_NAME)
            return DarkfiChatOutgoingQueueCrypto(app, encFile)
        }
    }
}
