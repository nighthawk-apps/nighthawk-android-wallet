package com.nighthawkapps.lib.android.sdk.chat

import android.content.Context
import com.google.crypto.tink.Aead
import com.nighthawkapps.lib.android.sdk.util.SecureDataStoreSerializer
import com.nighthawkapps.lib.android.spackle.Twig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Receipt phases survive process death. UI collects [revision]. */
object ChatReceiptStore {
    val revision = MutableStateFlow(0)
    private const val ENC_FILE_NAME = "chat_receipts_v1.enc"
    private const val LEGACY_FILE_NAME = "chat_receipts_v1.json"
    private const val KEYSET_NAME = "darkfi_chat_receipts"
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private val memory = LinkedHashMap<String, ChatReceipt>()

    @Volatile
    private var cachedAead: Aead? = null

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

    private fun aeadFor(context: Context): Aead? =
        cachedAead ?: synchronized(lock) {
            cachedAead ?: runCatching {
                SecureDataStoreSerializer.createAead(context.applicationContext, KEYSET_NAME)
            }.onSuccess {
                cachedAead = it
            }.onFailure { error ->
                Twig.warn(error) { "ChatReceiptStore: Tink AEAD unavailable" }
            }.getOrNull()
        }

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
        val model = readModel(context) ?: return
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

    private fun readModel(context: Context): FileModel? {
        val enc = encryptedFile(context)
        val aead = aeadFor(context)
        val encModel =
            if (enc.exists() && aead != null) {
                runCatching {
                    val bytes = enc.readBytes()
                    if (bytes.isEmpty()) null else json.decodeFromString<FileModel>(aead.decrypt(bytes, null).decodeToString())
                }.getOrNull()
            } else {
                null
            }
        val legacy = legacyFile(context)
        if (legacy.exists()) {
            val legacyModel = runCatching { json.decodeFromString<FileModel>(legacy.readText()) }.getOrNull()
            val merged =
                FileModel(
                    ((legacyModel?.items.orEmpty()) + (encModel?.items.orEmpty()))
                        .associateBy { it.id }
                        .values
                        .toList(),
                )
            if (writeModel(context, merged) || legacyModel == null) {
                runCatching { legacy.delete() }
            }
            return merged
        }
        return encModel
    }

    private fun writeModel(
        context: Context,
        model: FileModel,
    ): Boolean {
        val aead = aeadFor(context) ?: return false
        val ciphertext =
            runCatching { aead.encrypt(json.encodeToString(model).encodeToByteArray(), null) }
                .getOrNull() ?: return false
        val enc = encryptedFile(context)
        enc.parentFile?.mkdirs()
        val tmp = File(enc.parentFile, "${enc.name}.tmp")
        tmp.writeBytes(ciphertext)
        if (!tmp.renameTo(enc)) {
            enc.writeBytes(ciphertext)
            runCatching { tmp.delete() }
        }
        return true
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
        writeModel(context, model)
    }

    private fun encryptedFile(context: Context): File = File(context.applicationContext.filesDir, ENC_FILE_NAME)

    private fun legacyFile(context: Context): File = File(context.applicationContext.filesDir, LEGACY_FILE_NAME)
}
