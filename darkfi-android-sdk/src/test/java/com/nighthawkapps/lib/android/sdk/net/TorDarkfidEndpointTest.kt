package com.nighthawkapps.lib.android.sdk.net

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatPreferences
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TorDarkfidEndpointTest {
    private lateinit var application: Application

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        DarkfiChatPreferences(application).apply {
            privacyNetwork = PrivacyNetwork.Direct
            socksHost = "127.0.0.1"
            socksPort = 9050
        }
    }

    @Test
    fun passthrough_when_direct() {
        val endpoint =
            DarkfiEndpoint("relay.example", DarkfiEndpoint.LIGHTWALLET_GRPC_PORT, false)
        assertEquals(
            "tcp://relay.example:${DarkfiEndpoint.LIGHTWALLET_GRPC_PORT}",
            TorDarkfidEndpoint.displayUrlForWallet(application, endpoint),
        )
    }

    @Test
    fun loopback_unwrapped_when_tor_on() {
        DarkfiChatPreferences(application).privacyNetwork = PrivacyNetwork.Tor
        val endpoint = DarkfiEndpoint("127.0.0.1", DarkfiEndpoint.LIGHTWALLET_GRPC_PORT, false)
        assertEquals(
            "tcp://127.0.0.1:${DarkfiEndpoint.LIGHTWALLET_GRPC_PORT}",
            TorDarkfidEndpoint.displayUrlForWallet(application, endpoint),
        )
    }

    @Test
    fun socks5_uri_when_tor_on_and_remote_host() {
        DarkfiChatPreferences(application).privacyNetwork = PrivacyNetwork.Tor
        val endpoint = DarkfiEndpoint("node.dark.fi", DarkfiEndpoint.LIGHTWALLET_GRPC_PORT, false)
        assertEquals(
            "socks5://127.0.0.1:9050/node.dark.fi:${DarkfiEndpoint.LIGHTWALLET_GRPC_PORT}",
            TorDarkfidEndpoint.displayUrlForWallet(application, endpoint),
        )
    }

    @Test
    fun socks5_uri_uses_nym_port_when_nym() {
        DarkfiChatPreferences(application).privacyNetwork = PrivacyNetwork.Nym
        val endpoint = DarkfiEndpoint("node.dark.fi", DarkfiEndpoint.LIGHTWALLET_GRPC_PORT, false)
        assertEquals(
            "socks5://127.0.0.1:${PrivacyNetwork.DEFAULT_NYM_SOCKS_PORT}/node.dark.fi:${DarkfiEndpoint.LIGHTWALLET_GRPC_PORT}",
            TorDarkfidEndpoint.displayUrlForWallet(application, endpoint),
        )
    }

    @Test
    fun tls_endpoint_passthrough_when_nym_or_tor() {
        val endpoint =
            DarkfiEndpoint(
                host = "epidermis-sandbox-marshland.ngrok-free.dev",
                port = 443,
                isTls = true,
            )
        for (network in listOf(PrivacyNetwork.Nym, PrivacyNetwork.Tor)) {
            DarkfiChatPreferences(application).privacyNetwork = network
            assertEquals(
                "tcp+tls://epidermis-sandbox-marshland.ngrok-free.dev:443",
                TorDarkfidEndpoint.displayUrlForWallet(application, endpoint),
            )
        }
    }

    @Test
    fun custom_socks_host_in_uri() {
        val prefs = DarkfiChatPreferences(application)
        prefs.privacyNetwork = PrivacyNetwork.Tor
        prefs.socksHost = "10.0.0.5"
        prefs.socksPort = 9150
        val endpoint = DarkfiEndpoint("node.dark.fi", DarkfiEndpoint.LIGHTWALLET_GRPC_PORT, false)
        assertFalse(TorDarkfidEndpoint.displayUrlForWallet(application, endpoint).startsWith("tcp://"))
        assertEquals(
            "socks5://10.0.0.5:9150/node.dark.fi:${DarkfiEndpoint.LIGHTWALLET_GRPC_PORT}",
            TorDarkfidEndpoint.displayUrlForWallet(application, endpoint),
        )
    }
}
