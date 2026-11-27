package com.nighthawkapps.lib.android.sdk.chat.darkirc

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

data class ParsedDmCard(
    val publicKeyBase58: String,
    val nick: String? = null,
    val ratchetCapable: Boolean = false,
)

/**
 * Parses DarkIRC DM ChaCha public keys (32-byte bs58) from pasted text, `nighthawk://contact?...`
 * cards (supporting `&dr=1` for wallet-level Double Ratchet), or the canonical share prefix used in
 * public channels.
 */
object DarkircDmPubkeyParser {
    const val SHARE_PREFIX = "!darkfi-dm-pubkey:"
    private const val CONTACT_URI_PREFIX = "nighthawk://contact?"

    private val PREFIX_REGEX =
        Regex(
            Regex.escape(SHARE_PREFIX) +
                "([123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz]+)",
        )

    private val URI_REGEX =
        Regex("nighthawk://contact\\?[^\\s]+", RegexOption.IGNORE_CASE)

    private val BS58_TOKEN =
        Regex("[123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz]{32,44}")

    fun formatShareLine(publicKeyBase58: String): String = SHARE_PREFIX + publicKeyBase58.trim()

    fun formatContactCard(
        publicKeyBase58: String,
        nick: String? = null,
        ratchetCapable: Boolean = true,
    ): String {
        val params = mutableListOf("k=${URLEncoder.encode(publicKeyBase58.trim(), "UTF-8")}")
        val cleanNick = nick?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanNick != null) {
            params += "name=${URLEncoder.encode(cleanNick, "UTF-8")}"
        }
        if (ratchetCapable) {
            params += "dr=1"
        }
        return "$CONTACT_URI_PREFIX${params.joinToString("&")}"
    }

    /** Parse a pasted contact card, share line, or raw 32-byte bs58 DM public key. */
    fun parseCard(text: String): ParsedDmCard? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return null
        }
        URI_REGEX.find(trimmed)?.value?.let { rawUri ->
            val cleanUri = rawUri.replace(Regex("[.,;)>\\]]+$"), "")
            parseNighthawkContactUri(cleanUri)?.let { return it }
        }
        PREFIX_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.let { candidate ->
            if (DarkircBs58.isValidSecret32(candidate)) {
                return ParsedDmCard(publicKeyBase58 = candidate)
            }
        }
        for (match in BS58_TOKEN.findAll(trimmed)) {
            val candidate = match.value
            if (DarkircBs58.isValidSecret32(candidate)) {
                return ParsedDmCard(publicKeyBase58 = candidate)
            }
        }
        return null
    }

    private fun parseNighthawkContactUri(rawUri: String): ParsedDmCard? =
        runCatching {
            val uri = URI(rawUri)
            val query = uri.rawQuery ?: return null
            var key: String? = null
            var nick: String? = null
            var ratchet = false
            for (pair in query.split('&')) {
                val idx = pair.indexOf('=')
                if (idx <= 0) continue
                val k = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val v = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                when (k.lowercase(java.util.Locale.ROOT)) {
                    "k", "pubkey", "key", "dm" -> if (DarkircBs58.isValidSecret32(v.trim())) key = v.trim()
                    "name", "nick" -> nick = v.trim().takeIf { it.isNotEmpty() }
                    "dr" -> ratchet = v == "1" || v.equals("true", ignoreCase = true)
                }
            }
            key?.let { ParsedDmCard(publicKeyBase58 = it, nick = nick, ratchetCapable = ratchet) }
        }.getOrNull()

    /** First valid 32-byte bs58 key in [text], preferring contact card / canonical prefix. */
    fun extractFromText(text: String): String? = parseCard(text)?.publicKeyBase58
}
