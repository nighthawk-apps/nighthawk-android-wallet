package com.nighthawkapps.lib.android.sdk.chat

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persist messages typed while IRC is offline; drained after the next successful [DarkircIrcClient] session.
 *
 * Serialization uses **`kotlinx.serialization`** JSON (`PendingOutgoingChatFile`). A future **Room** layer
 * can mirror [PendingOutgoingChatRecord] as `@Entity` once KSP/Room is added to the project.
 */
class DarkfiChatOutgoingQueueStore internal constructor(
    private val queueFile: File,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = false
        }

    private val lock = Any()

    fun loadAll(): List<PendingOutgoingChatRecord> =
        synchronized(lock) {
            if (!queueFile.exists()) return@synchronized emptyList()
            runCatching {
                json.decodeFromString<PendingOutgoingChatFile>(queueFile.readText()).items
            }.getOrDefault(emptyList())
        }

    fun enqueue(record: PendingOutgoingChatRecord) {
        synchronized(lock) {
            val cur = loadAllUnlocked()
            val next = cur + record
            writeAllUnlocked(next)
        }
    }

    fun remove(id: String) {
        synchronized(lock) {
            val cur = loadAllUnlocked()
            writeAllUnlocked(cur.filterNot { it.id == id })
        }
    }

    fun clear() {
        synchronized(lock) {
            runCatching { queueFile.delete() }
        }
    }

    private fun loadAllUnlocked(): List<PendingOutgoingChatRecord> {
        if (!queueFile.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<PendingOutgoingChatFile>(queueFile.readText()).items
        }.getOrDefault(emptyList())
    }

    private fun writeAllUnlocked(items: List<PendingOutgoingChatRecord>) {
        val parent = queueFile.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        val bytes = json.encodeToString(PendingOutgoingChatFile(items = items)).encodeToByteArray()
        val tmp = File(queueFile.parentFile, "${queueFile.name}.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(queueFile)) {
            queueFile.writeBytes(bytes)
            runCatching { tmp.delete() }
        }
    }

    companion object {
        private const val FILE_NAME = "darkfi_chat_outgoing_queue.json"

        fun create(context: Context): DarkfiChatOutgoingQueueStore =
            DarkfiChatOutgoingQueueStore(File(context.applicationContext.filesDir, FILE_NAME))
    }
}
