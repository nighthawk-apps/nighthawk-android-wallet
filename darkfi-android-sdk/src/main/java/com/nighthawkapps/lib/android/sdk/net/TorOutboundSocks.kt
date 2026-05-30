package com.nighthawkapps.lib.android.sdk.net

import android.content.Context
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatPreferences
import com.nighthawkapps.lib.android.sdk.chat.TorIntegrationHelper
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * SOCKS policy aligned with IRC + wallet JSON-RPC:
 * — clearnet HTTP uses SOCKS whenever [DarkfiChatPreferences.routeOutboundThroughTor] is true;
 * — outbound TCP skips SOCKS for loopback (adb reverse / local darkfid).
 */
object TorOutboundSocks {
    private const val DEFAULT_HOST_IF_BLANK: String = "127.0.0.1"

    fun isLocalLoopbackHost(host: String): Boolean {
        val h = host.trim().ifBlank { DEFAULT_HOST_IF_BLANK }
        return h == DEFAULT_HOST_IF_BLANK ||
            h.equals("localhost", ignoreCase = true) ||
            h == "::1"
    }

    /**
     * OkHttp / Retrofit: when Tor outbound is enabled, all clearnet HTTP(S) uses the configured SOCKS proxy.
     */
    fun proxyForClearnetHttp(appContext: Context): Proxy? =
        torSocksProxyForDestination(appContext.applicationContext, tcpDestinationHost = null)

    /**
     * Raw TCP (e.g. darkfid JSON-RPC): SOCKS only for non-loopback endpoints when Tor is on.
     */
    fun proxyForOutboundTcpDestination(
        appContext: Context,
        destinationHost: String,
    ): Proxy? = torSocksProxyForDestination(appContext.applicationContext, destinationHost)

    /**
     * @param tcpDestinationHost When non-null **and** loopback, Tor may be on but the socket stays direct (adb /
     * local daemon). When **null** (HTTP client), SOCKS applies whenever Tor outbound is enabled.
     */
    private fun torSocksProxyForDestination(
        appContext: Context,
        tcpDestinationHost: String?,
    ): Proxy? {
        val prefs = DarkfiChatPreferences(appContext)
        val bypassSocks =
            !prefs.routeOutboundThroughTor ||
                (tcpDestinationHost != null && isLocalLoopbackHost(tcpDestinationHost))
        return if (bypassSocks) {
            null
        } else {
            Proxy(Proxy.Type.SOCKS, socksInetSocketAddress(prefs))
        }
    }

    private fun socksInetSocketAddress(prefs: DarkfiChatPreferences): InetSocketAddress {
        val socksHost =
            prefs.socksHost.trim().ifBlank { TorIntegrationHelper.DEFAULT_SOCKS_HOST }
        return InetSocketAddress(socksHost, prefs.socksPort)
    }
}
