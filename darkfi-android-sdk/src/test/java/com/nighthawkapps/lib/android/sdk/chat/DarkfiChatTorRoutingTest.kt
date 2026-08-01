package com.nighthawkapps.lib.android.sdk.chat

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DarkfiChatTorRoutingTest {
    @Test
    fun socksOnlyWhenTorOnAndNonLoopback() {
        assertFalse(shouldRouteIrcThroughTorSocks(useTorTransport = true, ircHost = "127.0.0.1"))
        assertFalse(shouldRouteIrcThroughTorSocks(useTorTransport = true, ircHost = "localhost"))
        assertFalse(shouldRouteIrcThroughTorSocks(useTorTransport = true, ircHost = "::1"))
        assertFalse(shouldRouteIrcThroughTorSocks(useTorTransport = true, ircHost = ""))
        assertTrue(shouldRouteIrcThroughTorSocks(useTorTransport = true, ircHost = "darkirc.example"))
        assertFalse(shouldRouteIrcThroughTorSocks(useTorTransport = false, ircHost = "darkirc.example"))
    }

    @Test
    fun socksWhenTorOn_forOnionHostname() {
        assertTrue(
            shouldRouteIrcThroughTorSocks(
                useTorTransport = true,
                ircHost = "g7fxelebievvpr27w7gt24lflptpw3jeeuvafovgliq5utdst6xyruyd.onion",
            ),
        )
    }

    @Test
    fun socksWhenTorOn_forLanIpv4() {
        assertTrue(shouldRouteIrcThroughTorSocks(useTorTransport = true, ircHost = "192.168.1.50"))
    }
}
