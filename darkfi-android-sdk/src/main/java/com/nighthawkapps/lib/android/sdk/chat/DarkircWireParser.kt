package com.nighthawkapps.lib.android.sdk.chat

/**
 * Parses DarkIRC server lines relevant to the Kotlin UI.
 *
 * Typical `PRIVMSG`:
 * `:nick!~anon@darkirc PRIVMSG #channel :hello`
 */
internal object DarkircWireParser {
    private val privmsgRegex =
        Regex("^:([^!\\s]+)\\S* PRIVMSG (\\S+) :(.*)$")

    fun parsePrivmsg(line: String): ChatChannelMessage? {
        val m = privmsgRegex.matchEntire(line.trimEnd('\r', '\n')) ?: return null
        val nick = m.groupValues[1]
        val channel = m.groupValues[2]
        val text = m.groupValues[3]
        return ChatChannelMessage(
            channel = channel,
            nick = nick,
            text = text,
            timestampMs = System.currentTimeMillis(),
        )
    }

    fun containsWelcomeNumeric(line: String): Boolean {
        val trimmed = line.trimEnd('\r', '\n')
        val tokens = trimmed.split(' ')
        val idx = tokens.indexOfFirst { it == "001" }
        return idx >= 0 && idx + 1 < tokens.size
    }

    fun isPing(line: String): Boolean =
        line.length >= 4 &&
            line.startsWith("PING", ignoreCase = true) &&
            (line.getOrNull(4)?.let { it == ' ' || it == ':' } == true)

    fun pongPayload(line: String): String {
        val idx = line.indexOf(':')
        return if (idx >= 0) line.substring(idx + 1).trimEnd('\r', '\n') else ""
    }
}
