package com.nighthawkapps.lib.android.sdk.chat.darkirc

/**
 * Parses DarkIRC DM ChaCha public keys (32-byte bs58) from pasted text or the canonical
 * share prefix used in public channels.
 */
object DarkircDmPubkeyParser {
    const val SHARE_PREFIX = "!darkfi-dm-pubkey:"

    private val PREFIX_REGEX =
        Regex(
            Regex.escape(SHARE_PREFIX) +
                "([123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz]+)",
        )

    private val BS58_TOKEN =
        Regex("[123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz]{43,44}")

    fun formatShareLine(publicKeyBase58: String): String = SHARE_PREFIX + publicKeyBase58.trim()

    /** First valid 32-byte bs58 key in [text], preferring the canonical prefix. */
    fun extractFromText(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return null
        }
        PREFIX_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.let { candidate ->
            if (DarkircBs58.isValidSecret32(candidate)) {
                return candidate
            }
        }
        for (match in BS58_TOKEN.findAll(trimmed)) {
            val candidate = match.value
            if (DarkircBs58.isValidSecret32(candidate)) {
                return candidate
            }
        }
        return null
    }
}
