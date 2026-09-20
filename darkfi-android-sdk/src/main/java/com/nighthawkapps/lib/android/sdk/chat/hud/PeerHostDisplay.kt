package com.nighthawkapps.lib.android.sdk.chat.hud

/**
 * Opaque HUD label for an outbound peer. Never reverse-looks-up hosts and never
 * returns a raw IP or `displayUrl` — those leak network location in the UI.
 */
object PeerHostDisplay {
    private val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    fun hostFromUrl(url: String?): String? {
        if (url.isNullOrBlank()) {
            return null
        }
        val afterScheme = url.substringAfter("://", missingDelimiterValue = "")
        if (afterScheme.isEmpty()) {
            return null
        }
        val hostPort = afterScheme.substringBefore('/').substringBefore('?')
        return if (hostPort.startsWith("[")) {
            hostPort.substringAfter("[").substringBefore("]").takeIf { it.isNotBlank() }
        } else {
            hostPort.substringBefore(':').takeIf { it.isNotBlank() }
        }
    }

    fun isOnion(host: String): Boolean = host.endsWith(".onion", ignoreCase = true)

    fun isIpLiteral(host: String): Boolean {
        if (IPV4.matches(host)) {
            return true
        }
        if (!host.contains(':')) {
            return false
        }
        return host.all { ch ->
            ch.isDigit() || ch in 'a'..'f' || ch in 'A'..'F' || ch == ':' || ch == '.'
        }
    }

    /**
     * Label shown in the Chat Network HUD peers dialog.
     *
     * Hostnames (and `.onion`) are returned as-is. IP literals become
     * `"peer N"` or a truncated hash — never the address itself.
     */
    fun dnsName(
        url: String?,
        placeholder: String,
        peerIndex: Int = 0,
    ): String {
        val host = hostFromUrl(url) ?: return placeholder
        if (isOnion(host)) {
            return host
        }
        if (isIpLiteral(host)) {
            return opaquePeerLabel(host, peerIndex)
        }
        return host
    }

    fun opaquePeerLabel(
        host: String,
        peerIndex: Int,
    ): String {
        val hash = host.hashCode().toUInt().toString(16).padStart(8, '0').take(6)
        return if (peerIndex >= 0) {
            "peer $peerIndex"
        } else {
            hash
        }
    }
}
