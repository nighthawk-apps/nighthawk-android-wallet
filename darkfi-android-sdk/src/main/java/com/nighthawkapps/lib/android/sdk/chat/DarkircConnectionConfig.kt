package com.nighthawkapps.lib.android.sdk.chat

/**
 * TCP IRC endpoint compatible with DarkIRC’s local server (`tcp://host:6667` by default).
 *
 * When [routeThroughTorSocks] is true, the socket is opened via SOCKS5 at [socksHost]:[socksPort]
 * (defaults: `127.0.0.1:9050`). Loopback IRC hosts skip SOCKS because DarkIRC listens locally.
 */
data class DarkircConnectionConfig(
    val host: String,
    val port: Int,
    val nickname: String,
    val username: String = "android",
    val realname: String = "NighthawkWallet",
    val ircPassword: String? = null,
    val routeThroughTorSocks: Boolean = false,
    val socksHost: String = "127.0.0.1",
    val socksPort: Int = 9050,
    val useTls: Boolean = false,
    val connectTimeoutMs: Int = 15_000,
)
