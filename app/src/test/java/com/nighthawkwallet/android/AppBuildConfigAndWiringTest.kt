package com.nighthawkwallet.android

import com.nighthawkapps.lib.android.network.util.RetrofitHelper
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatConnectionBridge
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiNetwork
import com.nighthawkapps.lib.android.ui.screen.changeserver.model.DarkfiEndpointCatalog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBuildConfigAndWiringTest {
    @After
    fun tearDown() {
        DarkfiChatConnectionBridge.detach()
    }

    @Test
    fun buildConfigMatchesFlavorAndReleaseInvariants() {
        val isTestnet = BuildConfig.FLAVOR == "darkfitestnet"
        if (isTestnet) {
            assertEquals("com.nighthawkwallet.android.testnet", BuildConfig.APPLICATION_ID)
        } else {
            assertEquals("com.nighthawkwallet.android", BuildConfig.APPLICATION_ID)
        }
        assertTrue(
            "VERSION_NAME must follow semantic release format",
            Regex("""^\d+\.\d+\.\d+$""").matches(BuildConfig.VERSION_NAME),
        )
        assertTrue("VERSION_CODE must be positive", BuildConfig.VERSION_CODE >= 30100820)
        // Production mainnet release must disable logcat output.
        if (!BuildConfig.DEBUG && BuildConfig.FLAVOR == "darkfimainnet") {
            assertFalse(BuildConfig.LOGCAT_ENABLED)
        }
    }

    @Test
    fun defaultEndpointsUseTlsForRemotePresets() {
        val localOrEmulatorHosts = setOf("127.0.0.1", "localhost", "::1", "[::1]", "10.0.2.2")
        for (network in listOf(DarkfiNetwork.Testnet, DarkfiNetwork.Mainnet)) {
            val presets = DarkfiEndpointCatalog.presets(network)
            assertTrue("Expected presets for $network", presets.isNotEmpty())
            val remotePresets = presets.filterNot { it.endpoint.host.lowercase() in localOrEmulatorHosts }
            assertTrue(
                "All non-local presets for $network must enforce TLS",
                remotePresets.all { it.endpoint.isTls },
            )
        }
        val testnetDefault = DarkfiEndpointCatalog.presets(DarkfiNetwork.Testnet).first().endpoint
        assertTrue("Default testnet remote preset must use TLS", testnetDefault.isTls)
        assertEquals(443, testnetDefault.port)
    }

    @Test
    fun coingeckoTlsPinningIncludesLeafAndBackupPins() {
        val pins = RetrofitHelper.coingeckoPins
        assertTrue(
            "CoinGecko pinning must include leaf + backup intermediate/root pins",
            pins.size >= 2,
        )
        assertEquals("Pins must be distinct", pins.size, pins.toSet().size)
        val sha256PinPattern = Regex("""^sha256/[A-Za-z0-9+/]{43}=$""")
        for (pin in pins) {
            assertTrue("Malformed SPKI pin: $pin", sha256PinPattern.matches(pin))
        }
    }

    @Test
    fun chatConnectionBridgeQueuesEarlyReconnectUntilAttach() {
        DarkfiChatConnectionBridge.detach()
        assertFalse(DarkfiChatConnectionBridge.requestReconnect())

        var reconnectCalls = 0
        val handler: () -> Unit = { reconnectCalls += 1 }
        DarkfiChatConnectionBridge.attach(handler)
        assertEquals(1, reconnectCalls)

        assertTrue(DarkfiChatConnectionBridge.requestReconnect())
        assertEquals(2, reconnectCalls)
    }
}
