package com.nighthawkapps.lib.android.ui.screen.chat

/**
 * One composer buffer per channel (`ch:`) or DM (`dm:`).
 * Switching rooms stashes the text so it is not sent to the wrong thread.
 */
internal object ChatDrafts {
    fun key(direct: Boolean, channel: String, dm: String?): String =
        if (direct) "dm:${dm.orEmpty()}" else "ch:$channel"

    fun switch(
        drafts: MutableMap<String, String>,
        fromKey: String,
        toKey: String,
        current: String,
    ): String {
        if (fromKey == toKey) return current
        if (current.isNotEmpty()) {
            drafts[fromKey] = current
        } else {
            drafts.remove(fromKey)
        }
        return drafts[toKey].orEmpty()
    }

    fun clear(drafts: MutableMap<String, String>, key: String) {
        drafts.remove(key)
    }
}
