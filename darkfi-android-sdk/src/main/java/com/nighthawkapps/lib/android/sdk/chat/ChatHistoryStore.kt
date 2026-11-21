package com.nighthawkapps.lib.android.sdk.chat

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.google.crypto.tink.Aead
import com.nighthawkapps.lib.android.sdk.util.SecureDataStoreSerializer
import com.nighthawkapps.lib.android.spackle.Twig
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Capped, Tink-AEAD–encrypted on-disk chat history so a restart still shows the last lines
 * without exposing DM or channel plaintext on disk.
 */
object ChatHistoryStore {
    private const val ENC_FILE_NAME = "chat_history_v1.enc"
    private const val LEGACY_PLAINTEXT_FILE_NAME = "chat_history_v1.json"
    private const val KEYSET_NAME = "darkfi_chat_history"
    private const val MAX_PER_THREAD = 200
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    private val lock = Any()

    @Volatile
    private var cachedAead: Aead? = null

    @VisibleForTesting
    internal var aeadOverride: Aead? = null

    @Serializable
    private data class FileModel(
        val lines: List<Line> = emptyList(),
    )

    @Serializable
    private data class Line(
        val eventId: String,
        val channel: String,
        val nick: String,
        val text: String,
        val timestampMs: Long,
        val isOutgoing: Boolean = false,
    )

    private fun aeadFor(context: Context): Aead? =
        aeadOverride ?: cachedAead ?: synchronized(lock) {
            aeadOverride ?: cachedAead ?: runCatching {
                SecureDataStoreSerializer.createAead(context.applicationContext, KEYSET_NAME)
            }.onSuccess {
                cachedAead = it
            }.onFailure { error ->
                Twig.warn(error) { "ChatHistoryStore: Tink AEAD unavailable" }
            }.getOrNull()
        }

    fun load(context: Context): Map<String, List<ChatChannelMessage>> =
        synchronized(lock) {
            val lines = read(context).lines
            lines.groupBy { it.channel }.mapValues { (_, rows) ->
                rows.takeLast(MAX_PER_THREAD).map { it.toMessage() }
            }
        }

    fun append(
        context: Context,
        message: ChatChannelMessage,
    ) {
        synchronized(lock) {
            val current = read(context).lines.toMutableList()
            if (current.any { it.eventId == message.eventId && it.channel == message.channel }) return
            current +=
                Line(
                    eventId = message.eventId,
                    channel = message.channel,
                    nick = message.nick,
                    text = message.text,
                    timestampMs = message.timestampMs,
                    isOutgoing = message.isOutgoing,
                )
            val capped =
                current
                    .groupBy { it.channel }
                    .flatMap { (_, rows) -> rows.takeLast(MAX_PER_THREAD) }
            write(context, FileModel(capped))
        }
    }

    private fun read(context: Context): FileModel {
        val enc = encryptedFile(context)
        val aead = aeadFor(context)
        val encryptedModel =
            if (enc.exists() && aead != null) {
                runCatching {
                    val ciphertext = enc.readBytes()
                    if (ciphertext.isEmpty()) {
                        FileModel()
                    } else {
                        val plain = aead.decrypt(ciphertext, null).decodeToString()
                        json.decodeFromString<FileModel>(plain)
                    }
                }.onFailure { error ->
                    Twig.warn(error) { "ChatHistoryStore: failed to decrypt history; starting empty" }
                }.getOrDefault(FileModel())
            } else {
                FileModel()
            }

        val legacy = legacyPlaintextFile(context)
        if (legacy.exists()) {
            val legacyModel =
                runCatching {
                    json.decodeFromString<FileModel>(legacy.readText())
                }.getOrDefault(FileModel())
            val merged =
                if (legacyModel.lines.isNotEmpty()) {
                    val combined =
                        (legacyModel.lines + encryptedModel.lines)
                            .distinctBy { it.channel to it.eventId }
                            .groupBy { it.channel }
                            .flatMap { (_, rows) -> rows.takeLast(MAX_PER_THREAD) }
                    val mergedModel = FileModel(combined)
                    if (write(context, mergedModel)) {
                        scrubAndDelete(legacy)
                    }
                    mergedModel
                } else {
                    scrubAndDelete(legacy)
                    encryptedModel
                }
            return merged
        }
        return encryptedModel
    }

    private fun write(
        context: Context,
        model: FileModel,
    ): Boolean {
        val aead = aeadFor(context)
        val plaintextBytes = json.encodeToString(model).encodeToByteArray()
        val encryptedBytes =
            aead?.let {
                runCatching { it.encrypt(plaintextBytes, null) }
                    .onFailure { error -> Twig.warn(error) { "ChatHistoryStore: encrypt failed" } }
                    .getOrNull()
            }
        if (encryptedBytes != null) {
            val enc = encryptedFile(context)
            enc.parentFile?.mkdirs()
            val tmp = File(enc.parentFile, "${enc.name}.tmp")
            tmp.writeBytes(encryptedBytes)
            if (!tmp.renameTo(enc)) {
                enc.writeBytes(encryptedBytes)
                runCatching { tmp.delete() }
            }
        }
        return encryptedBytes != null
    }

    private fun scrubAndDelete(file: File) {
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

    private fun encryptedFile(context: Context): File = File(context.applicationContext.filesDir, ENC_FILE_NAME)

    private fun legacyPlaintextFile(context: Context): File =
        File(
            context.applicationContext.filesDir,
            LEGACY_PLAINTEXT_FILE_NAME,
        )

    private fun Line.toMessage() =
        ChatChannelMessage(
            eventId = eventId,
            channel = channel,
            nick = nick,
            text = text,
            timestampMs = timestampMs,
            isOutgoing = isOutgoing,
        )
}
