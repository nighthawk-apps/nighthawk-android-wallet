package com.nighthawkapps.lib.android.sdk.net

import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatPreferences
import com.nighthawkapps.lib.android.sdk.chat.TorIntegrationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.InetSocketAddress

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TorOutboundSocksTest {
    private val application = RuntimeEnvironment.getApplication()

    @Test
    fun isLocalLoopbackHost_ipv4_ipv6_localhost_keyword_and_blank() {
        assertTrue(TorOutboundSocks.isLocalLoopbackHost("127.0.0.1"))
        assertTrue(TorOutboundSocks.isLocalLoopbackHost("LOCALHOST"))
        assertTrue(TorOutboundSocks.isLocalLoopbackHost("::1"))
        assertTrue(TorOutboundSocks.isLocalLoopbackHost(""))
        assertTrue(TorOutboundSocks.isLocalLoopbackHost("   "))
    }

    @Test
    fun isLocalLoopbackHost_falseForCleartextPeers() {
        assertFalse(TorOutboundSocks.isLocalLoopbackHost("10.0.0.6"))
        assertFalse(TorOutboundSocks.isLocalLoopbackHost("darkirc.example"))
    }

    @Test
    fun proxyForClearnetHttp_whenTorDisabled_isNull() {
        val prefs = DarkfiChatPreferences(application)
        prefs.routeOutboundThroughTor = false
        assertNull(TorOutboundSocks.proxyForClearnetHttp(application))
    }

    @Test
    fun proxyForClearnetHttp_whenTorEnabled_usesSocksInetAddressFromPrefs() {
        val prefs = DarkfiChatPreferences(application)
        prefs.routeOutboundThroughTor = true
        prefs.socksHost = ""
        prefs.socksPort = 9150

        val proxy = TorOutboundSocks.proxyForClearnetHttp(application)
        assertNotNull(proxy)
        assertEquals(InetSocketAddress(TorIntegrationHelper.DEFAULT_SOCKS_HOST, 9150), proxy!!.address())

        prefs.socksHost = "127.10.33.77"
        val proxyCustom = TorOutboundSocks.proxyForClearnetHttp(application)!!
        assertEquals(InetSocketAddress("127.10.33.77", 9150), proxyCustom.address())
    }

    @Test
    fun proxyForOutboundTcp_whenTorDisabled_isNull_evenForRemoteHosts() {
        val prefs = DarkfiChatPreferences(application)
        prefs.routeOutboundThroughTor = false
        assertNull(TorOutboundSocks.proxyForOutboundTcpDestination(application, "relay.example"))
    }

    @Test
    fun proxyForOutboundTcp_whenTorEnabled_loopbackBypassesSocksButHttpStillProxied() {
        val prefs = DarkfiChatPreferences(application)
        prefs.routeOutboundThroughTor = true
        prefs.socksHost = TorIntegrationHelper.DEFAULT_SOCKS_HOST
        prefs.socksPort = TorIntegrationHelper.DEFAULT_SOCKS_PORT

        assertNull(TorOutboundSocks.proxyForOutboundTcpDestination(application, "127.0.0.1"))
        assertNotNull(TorOutboundSocks.proxyForClearnetHttp(application))

        assertNotNull(TorOutboundSocks.proxyForOutboundTcpDestination(application, "relay.example"))
    }

    /**
     * When Tor outbound is enabled, IRC policy and JSON-RPC callers must converge on SOCKS for
     * [.onion] names (often treated as opaque strings, not IPv4 literals).
     */
    @Test
    fun proxyForOutboundTcp_torOn_returnsSocksProxyForOnionHost() {
        DarkfiChatPreferences(application).routeOutboundThroughTor = true
        assertNotNull(
            TorOutboundSocks.proxyForOutboundTcpDestination(
                application,
                "g7fxelebievvpr27w7gt24lflptpw3jeeuvafovgliq5utdst6xyruyd.onion",
            ),
        )
    }
}
