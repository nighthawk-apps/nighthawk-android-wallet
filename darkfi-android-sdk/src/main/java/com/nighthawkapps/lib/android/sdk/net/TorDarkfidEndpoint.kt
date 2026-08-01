package com.nighthawkapps.lib.android.sdk.net

import android.content.Context
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatPreferences
import com.nighthawkapps.lib.android.sdk.chat.TorIntegrationHelper
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiEndpoint

/**
 * Rewrites wallet `darkfid` / lightwallet endpoint URLs for Rust dialers when app-wide Tor is on.
 *
 * Non-loopback `tcp://host:port` becomes `socks5://proxy:port/host:port` (DarkFi transport URI).
 *
 * ## Lightwallet / gRPC notes
 *
 * The mobile FFI `LightwalletClient` parses this `socks5://…` form, dials the
 * destination through the local Tor SOCKS proxy, and uses cleartext `http://`
 * to the remote host over that tunnel (acceptable for a loopback Tor proxy).
 *
 * Prefer `https://` + TLS certificate pin through Tor when the lightwalletd
 * endpoint supports TLS: set the wallet URL to an https endpoint and supply
 * `lightwallet_tls_pin_sha256`. Cleartext remote via SOCKS is OK for local
 * Tor; production should pin TLS when available.
 */
object TorDarkfidEndpoint {
    fun displayUrlForWallet(
        appContext: Context,
        endpoint: DarkfiEndpoint,
    ): String = toConnectUrl(appContext, endpoint)

    /**
     * Plain TCP when Tor is off or host is loopback; otherwise DarkFi `socks5://proxy/dest:port` URI.
     *
     * For lightwallet, the Rust FFI rewrites `socks5://proxy/dest` → dial dest via SOCKS
     * with gRPC URL `http://dest` (or use https+pin separately when configured).
     */
    fun toConnectUrl(
        appContext: Context,
        endpoint: DarkfiEndpoint,
    ): String {
        val host = endpoint.host.trim()
        val prefs = DarkfiChatPreferences(appContext.applicationContext)
        if (!prefs.routeOutboundThroughTor || TorOutboundSocks.isLocalLoopbackHost(host)) {
            return endpoint.toDisplayString()
        }
        val socksHost =
            prefs.socksHost.trim().ifBlank { TorIntegrationHelper.DEFAULT_SOCKS_HOST }
        val socksPort = prefs.socksPort
        return "socks5://$socksHost:$socksPort/$host:${endpoint.port}"
    }
}
