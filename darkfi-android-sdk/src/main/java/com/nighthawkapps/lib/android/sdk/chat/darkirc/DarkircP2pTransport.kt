package com.nighthawkapps.lib.android.sdk.chat.darkirc

/**
 * Embedded darkirc `[net]` profile — mirrors upstream clearnet vs Tor (`use_tor.txt` / `socks5` profile).
 */
sealed interface DarkircP2pTransport {
    data object Clearnet : DarkircP2pTransport

    data class TorViaSocks5(
        val socksHost: String,
        val socksPort: Int,
    ) : DarkircP2pTransport
}
