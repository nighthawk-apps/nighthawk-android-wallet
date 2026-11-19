package com.nighthawkapps.lib.android.sdk.chat

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Capped on-disk chat history so a restart still shows the last lines. */
object ChatHistoryStore {
    private const val FILE_NAME = "chat_history_v1.json"
    private const val MAX_PER_THREAD = 200
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    private val lock = Any()

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

    fun load(context: Context): Map<String, List<ChatChannelMessage>> {
        val lines = read(context).lines
        return lines.groupBy { it.channel }.mapValues { (_, rows) ->
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
        val file = file(context)
        if (!file.exists()) return FileModel()
        val raw = runCatching { file.readText() }.getOrNull() ?: return FileModel()
        return runCatching { json.decodeFromString<FileModel>(raw) }.getOrDefault(FileModel())
    }

    private fun write(
        context: Context,
        model: FileModel,
    ) {
        val file = file(context)
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(json.encodeToString(model))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    private fun file(context: Context): File = File(context.applicationContext.filesDir, FILE_NAME)

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
