package com.nighthawkapps.lib.android.sdk.chat

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Receipt phases survive process death. UI collects [revision]. */
object ChatReceiptStore {
    val revision = MutableStateFlow(0)
    private const val FILE_NAME = "chat_receipts_v1.json"
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private val memory = LinkedHashMap<String, ChatReceipt>()

    @Serializable
    private data class FileModel(
        val items: List<Stored> = emptyList(),
    )

    @Serializable
    private data class Stored(
        val id: String,
        val phase: String,
        val amount: String,
        val address: String,
        val memo: String? = null,
        val txId: String? = null,
    )

    fun upsert(
        context: Context,
        receipt: ChatReceipt,
    ) {
        synchronized(lock) {
            ensureLoaded(context)
            memory[receipt.id] = receipt
            persist(context)
        }
        revision.value = revision.value + 1
    }

    fun get(
        context: Context,
        id: String,
    ): ChatReceipt? {
        synchronized(lock) {
            ensureLoaded(context)
            return memory[id]
        }
    }

    fun mark(
        context: Context,
        id: String,
        phase: ChatReceiptPhase,
        txId: String? = null,
    ) {
        synchronized(lock) {
            ensureLoaded(context)
            val current = memory[id] ?: return
            memory[id] =
                current.copy(
                    phase = phase,
                    txId = txId ?: current.txId,
                )
            persist(context)
        }
        revision.value = revision.value + 1
    }

    private fun ensureLoaded(context: Context) {
        if (memory.isNotEmpty()) return
        val file = file(context)
        if (!file.exists()) return
        val raw = runCatching { file.readText() }.getOrNull() ?: return
        val model = runCatching { json.decodeFromString<FileModel>(raw) }.getOrNull() ?: return
        for (item in model.items) {
            val phase =
                runCatching { ChatReceiptPhase.valueOf(item.phase) }.getOrDefault(ChatReceiptPhase.Pending)
            memory[item.id] =
                ChatReceipt(
                    id = item.id,
                    phase = phase,
                    amount = item.amount,
                    address = item.address,
                    memo = item.memo,
                    txId = item.txId,
                )
        }
    }

    private fun persist(context: Context) {
        val model =
            FileModel(
                memory.values.map {
                    Stored(
                        id = it.id,
                        phase = it.phase.name,
                        amount = it.amount,
                        address = it.address,
                        memo = it.memo,
                        txId = it.txId,
                    )
                },
            )
        val file = file(context)
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(model))
    }

    private fun file(context: Context): File = File(context.applicationContext.filesDir, FILE_NAME)
}
