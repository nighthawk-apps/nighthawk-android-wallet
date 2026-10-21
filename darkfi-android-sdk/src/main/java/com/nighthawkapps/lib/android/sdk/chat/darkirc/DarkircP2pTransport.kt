package com.nighthawkapps.lib.android.sdk.chat.darkirc

/**
 * Embedded darkirc `[net]` profile: clearnet, Tor (`socks5` profile), or Nym (`socks5+tls` mixing).
 */
sealed interface DarkircP2pTransport {
    data object Clearnet : DarkircP2pTransport

    data class NymViaSocks5(
        val socksHost: String,
        val socksPort: Int,
    ) : DarkircP2pTransport

    data class TorViaSocks5(
        val socksHost: String,
        val socksPort: Int,
    ) : DarkircP2pTransport
}
