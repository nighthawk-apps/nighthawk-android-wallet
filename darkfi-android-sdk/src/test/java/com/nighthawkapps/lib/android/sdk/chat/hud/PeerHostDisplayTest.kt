package com.nighthawkapps.lib.android.sdk.chat.hud

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PeerHostDisplayTest {
    @Test
    fun hostFromUrl_parsesHostnameIpv4Ipv6AndOnion() {
        assertEquals(
            "seed.testnet.dark.fi",
            PeerHostDisplay.hostFromUrl("tcp+tls://seed.testnet.dark.fi:25588"),
        )
        assertEquals("1.2.3.4", PeerHostDisplay.hostFromUrl("tcp://1.2.3.4:9601"))
        assertEquals("2001:db8::1", PeerHostDisplay.hostFromUrl("tcp+tls://[2001:db8::1]:9601"))
        assertEquals("abc.onion", PeerHostDisplay.hostFromUrl("tor://abc.onion:9601"))
        assertNull(PeerHostDisplay.hostFromUrl(null))
        assertNull(PeerHostDisplay.hostFromUrl("connected"))
    }

    @Test
    fun dnsName_usesHostnameWithoutLookup() {
        val name =
            PeerHostDisplay.dnsName(
                url = "tcp+tls://lilith0.dark.fi:25588",
                placeholder = "sleeping",
                peerIndex = 0,
            )
        assertEquals("lilith0.dark.fi", name)
        assertFalse(name.contains(Regex("""\d{1,3}(\.\d{1,3}){3}""")))
    }

    @Test
    fun dnsName_keepsOnionWithoutLookup() {
        val name =
            PeerHostDisplay.dnsName(
                url = "tor://abcxyz.onion:9601",
                placeholder = "connected",
                peerIndex = 1,
            )
        assertEquals("abcxyz.onion", name)
    }

    @Test
    fun dnsName_doesNotRenderIpv4() {
        val url = "tcp://8.8.8.8:9601"
        val name =
            PeerHostDisplay.dnsName(
                url = url,
                placeholder = "connected",
                peerIndex = 2,
            )
        assertEquals("peer 2", name)
        assertFalse(name.contains("8.8.8.8"))
        assertFalse(name.contains(url))
        assertFalse(PeerHostDisplay.isIpLiteral(name))
    }

    @Test
    fun dnsName_doesNotRenderIpv6() {
        val url = "tcp+tls://[2001:db8::1]:9601"
        val name =
            PeerHostDisplay.dnsName(
                url = url,
                placeholder = "connected",
                peerIndex = 0,
            )
        assertEquals("peer 0", name)
        assertFalse(name.contains("2001:db8::1"))
        assertFalse(name.contains(url))
    }

    @Test
    fun dnsName_doesNotRenderPrivateIp() {
        val name =
            PeerHostDisplay.dnsName(
                url = "tcp://10.0.0.1:1",
                placeholder = "connected",
                peerIndex = 1,
            )
        assertEquals("peer 1", name)
        assertFalse(name.contains("10.0.0.1"))
    }

    @Test
    fun dnsName_placeholderWhenUrlMissing() {
        assertEquals(
            "sleeping",
            PeerHostDisplay.dnsName(url = null, placeholder = "sleeping"),
        )
    }

    @Test
    fun isIpLiteral_detectsV4AndV6() {
        assertTrue(PeerHostDisplay.isIpLiteral("1.2.3.4"))
        assertTrue(PeerHostDisplay.isIpLiteral("2001:db8::1"))
        assertFalse(PeerHostDisplay.isIpLiteral("seed.dark.fi"))
        assertFalse(PeerHostDisplay.isIpLiteral("peer 0"))
        assertTrue(PeerHostDisplay.isOnion("abc.onion"))
        assertFalse(PeerHostDisplay.isOnion("seed.dark.fi"))
    }
}
