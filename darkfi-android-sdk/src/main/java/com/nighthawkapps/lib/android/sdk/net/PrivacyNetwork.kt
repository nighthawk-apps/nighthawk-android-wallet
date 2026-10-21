package com.nighthawkapps.lib.android.sdk.net

/**
 * Network that carries all app traffic: lightwalletd sync and broadcast, darkfid JSON-RPC, clearnet
 * HTTP such as price quotes, and DarkIRC / darkfid P2P.
 *
 * Over Nym, P2P dials the clearnet `tcp+tls` seeds and peers through the Nym SOCKS proxy; onion-only
 * peers are unreachable through the mixnet and are skipped.
 */
enum class PrivacyNetwork(
    val prefValue: String
) {
    /** In-process Nym mixnet SOCKS proxy (default). */
    Nym("nym"),

    /** In-process Arti, or an external Tor SOCKS proxy. */
    Tor("tor"),

    /** No proxy. The server sees the device IP. */
    Direct("direct");

    companion object {
        val DEFAULT: PrivacyNetwork = Nym

        const val NYM_SOCKS_HOST: String = "127.0.0.1"
        const val DEFAULT_NYM_SOCKS_PORT: Int = 1080

        fun fromPref(value: String?): PrivacyNetwork = entries.firstOrNull { it.prefValue == value } ?: DEFAULT
    }
}
