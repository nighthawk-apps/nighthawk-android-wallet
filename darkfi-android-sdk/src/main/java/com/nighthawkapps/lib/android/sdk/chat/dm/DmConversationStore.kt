package com.nighthawkapps.lib.android.sdk.chat.dm

import android.content.Context
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatSecureStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal object DmConversationStore {
    private const val KEY_CONVERSATIONS_JSON = "darkirc_dm_conversations_json"

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    fun loadAll(context: Context): List<DmConversationMeta> {
        val raw =
            DarkfiChatSecureStore
                .getString(context.applicationContext, KEY_CONVERSATIONS_JSON)
                ?: return emptyList()
        return runCatching { json.decodeFromString<List<DmConversationMeta>>(raw) }
            .getOrDefault(emptyList())
    }

    fun saveAll(
        context: Context,
        list: List<DmConversationMeta>,
    ) {
        DarkfiChatSecureStore
            .putString(context.applicationContext, KEY_CONVERSATIONS_JSON, json.encodeToString(list))
    }

    fun upsert(
        context: Context,
        meta: DmConversationMeta,
    ) {
        val current = loadAll(context).filterNot { it.contactLabel == meta.contactLabel }
        saveAll(context, current + meta)
    }

    fun remove(
        context: Context,
        contactLabel: String,
    ) {
        saveAll(context, loadAll(context).filterNot { it.contactLabel == contactLabel })
    }

    fun touchMessage(
        context: Context,
        contactLabel: String,
        preview: String,
        atMs: Long,
    ) {
        val existing = loadAll(context).find { it.contactLabel == contactLabel }
        upsert(
            context,
            (existing ?: DmConversationMeta(contactLabel = contactLabel)).copy(
                lastMessagePreview = preview.take(DmConversationLimits.PREVIEW_MAX_LEN),
                lastMessageAtMs = atMs,
            ),
        )
    }
}

object DmConversationLimits {
    const val PREVIEW_MAX_LEN = 120
    const val CONTACT_LABEL_MAX_LEN = 32
}
