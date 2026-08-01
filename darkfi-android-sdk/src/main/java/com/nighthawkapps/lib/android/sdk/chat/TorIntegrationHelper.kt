package com.nighthawkapps.lib.android.sdk.chat

/**
 * Default loopback SOCKS5 target for bundled [tor-android] and advanced setups that point HTTP/IRC at
 * a local Tor SOCKS listener.
 */
object TorIntegrationHelper {
    const val DEFAULT_SOCKS_HOST: String = "127.0.0.1"
    const val DEFAULT_SOCKS_PORT: Int = 9050

    /**
     * Alternate SOCKS port some Tor builds expose; [TorSocksReadiness] probes it after the configured port.
     */
    const val ALT_SOCKS_PORT: Int = 9150
}
