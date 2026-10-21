package com.nighthawkapps.lib.android.sdk.net

import android.content.Context
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatPreferences
import com.nighthawkapps.lib.android.sdk.chat.TorIntegrationHelper
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * SOCKS policy for wallet traffic, following [DarkfiChatPreferences.privacyNetwork]:
 * — clearnet HTTP uses the Nym or Tor SOCKS proxy unless the network is [PrivacyNetwork.Direct];
 * — outbound TCP skips SOCKS for loopback (adb reverse / local darkfid).
 */
object TorOutboundSocks {
    private const val DEFAULT_HOST_IF_BLANK: String = "127.0.0.1"

    fun isLocalLoopbackHost(host: String): Boolean {
        val h = host.trim().ifBlank { DEFAULT_HOST_IF_BLANK }
        return h == DEFAULT_HOST_IF_BLANK ||
            h.equals("localhost", ignoreCase = true) ||
            h == "::1" ||
            h == "127.0.0.1" ||
            // Android emulator alias for the host machine's loopback.
            h == "10.0.2.2"
    }

    /**
     * OkHttp / Retrofit: all clearnet HTTP(S) uses the wallet network's SOCKS proxy.
     */
    fun proxyForClearnetHttp(appContext: Context): Proxy? =
        socksProxyForDestination(appContext.applicationContext, tcpDestinationHost = null)

    /**
     * Raw TCP (e.g. darkfid JSON-RPC): SOCKS only for non-loopback endpoints.
     */
    fun proxyForOutboundTcpDestination(
        appContext: Context,
        destinationHost: String,
    ): Proxy? = socksProxyForDestination(appContext.applicationContext, destinationHost)

    /** SOCKS `(host, port)` for the wallet network, or null for [PrivacyNetwork.Direct]. */
    fun walletSocksEndpoint(prefs: DarkfiChatPreferences): InetSocketAddress? =
        when (prefs.privacyNetwork) {
            PrivacyNetwork.Nym ->
                InetSocketAddress(PrivacyNetwork.NYM_SOCKS_HOST, prefs.nymSocksPort)
            PrivacyNetwork.Tor ->
                InetSocketAddress(
                    prefs.socksHost.trim().ifBlank { TorIntegrationHelper.DEFAULT_SOCKS_HOST },
                    prefs.socksPort,
                )
            PrivacyNetwork.Direct -> null
        }

    /**
     * @param tcpDestinationHost When non-null **and** loopback, the socket stays direct (adb /
     * local daemon). When **null** (HTTP client), SOCKS applies whenever a privacy network is on.
     */
    private fun socksProxyForDestination(
        appContext: Context,
        tcpDestinationHost: String?,
    ): Proxy? {
        if (tcpDestinationHost != null && isLocalLoopbackHost(tcpDestinationHost)) {
            return null
        }
        val endpoint = walletSocksEndpoint(DarkfiChatPreferences(appContext)) ?: return null
        return Proxy(Proxy.Type.SOCKS, endpoint)
    }
}
