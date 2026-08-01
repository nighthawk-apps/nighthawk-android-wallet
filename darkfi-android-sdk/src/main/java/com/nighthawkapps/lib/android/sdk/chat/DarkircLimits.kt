package com.nighthawkapps.lib.android.sdk.chat

internal object DarkircLimits {
    const val MAX_NICK_LEN: Int = 24
}

/** Mirrors whitespace + length constraints applied before `PRIVMSG`. */
internal object DarkircPrivmsgRules {
    const val MAX_BODY_LEN: Int = 512

    fun sanitizeBody(text: String): String? {
        val s =
            text
                .replace("\r", " ")
                .replace("\n", " ")
                .trim()
                .take(MAX_BODY_LEN)
        return s.ifBlank { null }
    }
}
