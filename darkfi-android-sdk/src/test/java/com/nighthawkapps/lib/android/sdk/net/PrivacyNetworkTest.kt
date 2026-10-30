package com.nighthawkapps.lib.android.sdk.net

import android.app.Application
import android.content.Context
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PrivacyNetworkTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()

    @Before
    fun clearPrefs() {
        app
            .getSharedPreferences("darkfi_chat_transport", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun fromPref_mapsEveryStoredValue() {
        PrivacyNetwork.entries.forEach { network ->
            assertEquals(network, PrivacyNetwork.fromPref(network.prefValue))
        }
    }

    @Test
    fun fromPref_unknownOrMissing_fallsBackToNym() {
        assertEquals(PrivacyNetwork.Nym, PrivacyNetwork.fromPref(null))
        assertEquals(PrivacyNetwork.Nym, PrivacyNetwork.fromPref(""))
        assertEquals(PrivacyNetwork.Nym, PrivacyNetwork.fromPref("i2p"))
    }

    @Test
    fun freshInstall_walletUsesNymOnDefaultPort() {
        val prefs = DarkfiChatPreferences(app)
        assertEquals(PrivacyNetwork.Nym, prefs.privacyNetwork)
        assertEquals(PrivacyNetwork.DEFAULT_NYM_SOCKS_PORT, prefs.nymSocksPort)
    }

    @Test
    fun privacyNetwork_persistsAcrossInstances() {
        DarkfiChatPreferences(app).privacyNetwork = PrivacyNetwork.Tor
        assertEquals(PrivacyNetwork.Tor, DarkfiChatPreferences(app).privacyNetwork)
        DarkfiChatPreferences(app).privacyNetwork = PrivacyNetwork.Direct
        assertEquals(PrivacyNetwork.Direct, DarkfiChatPreferences(app).privacyNetwork)
    }

    @Test
    fun chatTorFollowsTheAppNetwork() {
        val prefs = DarkfiChatPreferences(app)
        assertFalse(prefs.routeOutboundThroughTor)

        prefs.routeOutboundThroughTor = false
        assertEquals(PrivacyNetwork.Nym, prefs.privacyNetwork)

        prefs.routeOutboundThroughTor = true
        assertEquals(PrivacyNetwork.Tor, prefs.privacyNetwork)

        prefs.privacyNetwork = PrivacyNetwork.Nym
        assertFalse(prefs.routeOutboundThroughTor)

        prefs.privacyNetwork = PrivacyNetwork.Tor
        prefs.routeOutboundThroughTor = false
        assertEquals(PrivacyNetwork.Direct, prefs.privacyNetwork)
    }
}
