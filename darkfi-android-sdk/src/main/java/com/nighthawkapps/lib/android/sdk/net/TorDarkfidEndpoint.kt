package com.nighthawkapps.lib.android.sdk.net

import android.content.Context
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatPreferences
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiEndpoint

/**
 * Rewrites wallet lightwalletd endpoint URLs for Rust dialers when the wallet network is Nym or Tor.
 *
 * Non-loopback cleartext `tcp://host:port` becomes `socks5://proxy:port/host:port`, where the proxy is
 * the Nym or Tor SOCKS port for [DarkfiChatPreferences.privacyNetwork].
 *
 * ## Lightwallet / gRPC notes
 *
 * TLS endpoints (`tcp+tls://…`, e.g. Studio ngrok on :443) are **not** rewritten.
 * Rust already installs a process-wide SOCKS5 proxy when `useNym` or `useTor` is set on
 * bootstrap, and must keep `https://` so certificate pinning can run. Wrapping
 * TLS URLs as `socks5://…` used to force cleartext `http://` and trip
 * `require_https_over_socks`.
 *
 * Cleartext remote via SOCKS remains available for local/dev lightwalletd.
 */
object TorDarkfidEndpoint {
    fun displayUrlForWallet(
        appContext: Context,
        endpoint: DarkfiEndpoint,
    ): String = toConnectUrl(appContext, endpoint)

    /**
     * Plain / TLS URL when the wallet network is direct, the host is loopback, or the endpoint uses
     * TLS; otherwise DarkFi `socks5://proxy/dest:port` for cleartext remotes.
     */
    fun toConnectUrl(
        appContext: Context,
        endpoint: DarkfiEndpoint,
    ): String {
        val host = endpoint.host.trim()
        val prefs = DarkfiChatPreferences(appContext.applicationContext)
        val socks = TorOutboundSocks.walletSocksEndpoint(prefs)
        if (socks == null || TorOutboundSocks.isLocalLoopbackHost(host) || endpoint.isTls) {
            return endpoint.toDisplayString()
        }
        return "socks5://${socks.hostString}:${socks.port}/$host:${endpoint.port}"
    }
}
