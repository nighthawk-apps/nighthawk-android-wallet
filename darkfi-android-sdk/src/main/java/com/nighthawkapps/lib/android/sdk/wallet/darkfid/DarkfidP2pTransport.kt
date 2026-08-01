package com.nighthawkapps.lib.android.sdk.wallet.darkfid

/** Embedded darkfid `[network_config.*.net]` profile (clearnet vs Tor-via-SOCKS). */
sealed interface DarkfidP2pTransport {
    data object Clearnet : DarkfidP2pTransport

    data class TorViaSocks5(
        val socksHost: String,
        val socksPort: Int,
    ) : DarkfidP2pTransport
}
