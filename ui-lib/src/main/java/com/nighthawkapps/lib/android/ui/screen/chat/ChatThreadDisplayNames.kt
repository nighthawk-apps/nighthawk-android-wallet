package com.nighthawkapps.lib.android.ui.screen.chat

import android.content.Context
import com.nighthawkapps.lib.android.ui.preference.StandardPreferenceKeys
import com.nighthawkapps.lib.android.ui.preference.StandardPreferenceSingleton
import org.json.JSONObject

/** Optional local-only display name per chat thread. Not a global Session-style ID. */
object ChatThreadDisplayNames {
    suspend fun load(context: Context): Map<String, String> {
        val prefs = StandardPreferenceSingleton.getInstance(context.applicationContext)
        val raw = StandardPreferenceKeys.CHAT_THREAD_DISPLAY_NAMES.getValue(prefs)
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                obj.keys().forEach { key ->
                    val value = obj.optString(key).trim()
                    if (value.isNotEmpty()) put(key, value)
                }
            }
        }.getOrDefault(emptyMap())
    }

    suspend fun put(
        context: Context,
        threadKey: String,
        displayName: String,
    ) {
        val key = threadKey.trim()
        if (key.isEmpty()) return
        val next = load(context).toMutableMap()
        val name = displayName.trim().take(32)
        if (name.isEmpty()) {
            next.remove(key)
        } else {
            next[key] = name
        }
        persist(context, next)
    }

    fun display(
        map: Map<String, String>,
        threadKey: String,
    ): String = map[threadKey]?.takeIf { it.isNotBlank() } ?: threadKey

    private suspend fun persist(
        context: Context,
        map: Map<String, String>,
    ) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        val prefs = StandardPreferenceSingleton.getInstance(context.applicationContext)
        StandardPreferenceKeys.CHAT_THREAD_DISPLAY_NAMES.putValue(prefs, obj.toString())
    }
}
