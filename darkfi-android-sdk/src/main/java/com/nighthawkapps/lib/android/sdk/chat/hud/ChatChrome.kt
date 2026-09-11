package com.nighthawkapps.lib.android.sdk.chat.hud

import java.util.Locale

/**
 * Chat chroma for DarkIRC — DarkFi-app density (hashed nicks + tappable URLs)
 * recast in Nighthawk Stealth tokens. Same two-tone body is used for public
 * group channels and encrypted DMs: incoming = body steel, outgoing = header
 * ice. Encrypted threads add charcoal / accent-subtle bubbles.
 *
 * Token map (do not introduce DarkFi cyan `#00F0FF`):
 * - Own nick → accent `#5E9BAF`
 * - Peer nicks → hashed Stealth palette
 * - Links → accent muted `#7DADB9`
 * - Incoming body → text body `#C4CBD4`
 * - Outgoing body → text header `#E8EBEF`
 */
object ChatChrome {
    const val OWN_NICK: Int = 0xFF5E9BAF.toInt()
    const val LINK: Int = 0xFF7DADB9.toInt()
    const val FUD: Int = 0xFF8F98A3.toInt()
    const val BODY_INCOMING: Int = 0xFFC4CBD4.toInt()
    const val BODY_OUTGOING: Int = 0xFFE8EBEF.toInt()
    const val TIMESTAMP: Int = 0xFF8F98A3.toInt()
    const val BUBBLE_INCOMING: Int = 0xFF171C22.toInt()
    const val BUBBLE_OUTGOING: Int = 0xFF243038.toInt()

    /** Peer nick hues — cool slate family, no neon magenta. */
    val PEER_NICKS: IntArray =
        intArrayOf(
            0xFF7DADB9.toInt(), // accent muted
            0xFF7AA3F3.toInt(), // bright blue
            0xFF9BBDCF.toInt(), // cool slate
            0xFF9DEA79.toInt(), // bright green
            0xFFA8B2BD.toInt(), // navigation muted
            0xFF9C5776.toInt(), // plum
            0xFF4F8799.toInt(), // accent pressed
            0xFFC5CED6.toInt(), // parmaviolet
        )

    fun isOwnNick(
        nick: String,
        myNick: String,
    ): Boolean {
        if (nick.isBlank() || myNick.isBlank()) {
            return false
        }
        return nick.equals(myNick, ignoreCase = true)
    }

    fun nickArgb(
        nick: String,
        myNick: String,
    ): Int {
        if (nick.equals("System", ignoreCase = true)) {
            return TIMESTAMP
        }
        return if (isOwnNick(nick, myNick)) OWN_NICK else peerNickArgb(nick)
    }

    fun peerNickIndex(nick: String): Int {
        var hash = 0
        for (ch in nick.lowercase(Locale.ROOT)) {
            hash = (hash * 31 + ch.code) and 0x7FFF_FFFF
        }
        return hash % PEER_NICKS.size
    }

    fun peerNickArgb(nick: String): Int = PEER_NICKS[peerNickIndex(nick)]

    fun bodyArgb(isOwn: Boolean): Int = if (isOwn) BODY_OUTGOING else BODY_INCOMING

    fun bubbleArgb(isOwn: Boolean): Int = if (isOwn) BUBBLE_OUTGOING else BUBBLE_INCOMING

    /**
     * Direct inbox is always E2E. A `#channel` is encrypted when it has a stored
     * shared secret (NaCl box). Same two-tone body applies to both; encrypted
     * threads also get charcoal / accent-subtle bubbles.
     */
    fun threadIsEncrypted(
        isDirectInbox: Boolean,
        threadKey: String,
        encryptedChannelNames: Collection<String>,
    ): Boolean {
        if (isDirectInbox) {
            return true
        }
        val key = threadKey.lowercase(Locale.ROOT)
        if (key.isBlank()) {
            return false
        }
        return encryptedChannelNames.any { it.lowercase(Locale.ROOT) == key }
    }
}

/** @deprecated Use [ChatChrome.peerNickArgb]; kept for existing call sites. */
@Deprecated("Use ChatChrome", ReplaceWith("ChatChrome.peerNickArgb(nick)"))
object NickPalette {
    val ARGB: IntArray = ChatChrome.PEER_NICKS

    fun argbFor(nick: String): Int = ChatChrome.peerNickArgb(nick)
}
