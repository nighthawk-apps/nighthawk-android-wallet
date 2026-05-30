package com.nighthawkapps.lib.android.sdk.chat

import com.nighthawkapps.lib.android.sdk.net.TorOutboundSocks

/** Pure policy for when the IRC hop uses a Tor SOCKS5 listener vs direct TCP. */
internal fun shouldRouteIrcThroughTorSocks(
    useTorTransport: Boolean,
    ircHost: String,
): Boolean = useTorTransport && !TorOutboundSocks.isLocalLoopbackHost(ircHost)
