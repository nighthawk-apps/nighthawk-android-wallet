package com.nighthawkapps.lib.android.sdk.chat

import android.app.Application
import android.content.Context
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
class DarkfiChatPreferencesEmbeddedTorTest {
    @Before
    fun clearPrefs() {
        val app = RuntimeEnvironment.getApplication() as Application
        app
            .getSharedPreferences("darkfi_chat_transport", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    @Test
    fun useEmbeddedTor_defaultsToTrue() {
        val app = RuntimeEnvironment.getApplication() as Application
        val prefs = DarkfiChatPreferences(app)
        // Matches iOS embedded Tor default (parity for wallet/chat transport).
        assertTrue(prefs.useEmbeddedTor)
    }

    @Test
    fun routeOutboundThroughTor_defaultsToFalse() {
        val app = RuntimeEnvironment.getApplication() as Application
        val prefs = DarkfiChatPreferences(app)
        assertFalse(prefs.routeOutboundThroughTor)
    }

    @Test
    fun useEmbeddedTor_roundTrip() {
        val app = RuntimeEnvironment.getApplication() as Application
        val prefs = DarkfiChatPreferences(app)
        prefs.useEmbeddedTor = false
        assertFalse(prefs.useEmbeddedTor)
        prefs.useEmbeddedTor = true
        assertTrue(prefs.useEmbeddedTor)
    }

    @Test
    fun runEmbeddedDarkirc_defaultsToTrue() {
        val app = RuntimeEnvironment.getApplication() as Application
        val prefs = DarkfiChatPreferences(app)
        assertTrue(prefs.runEmbeddedDarkirc)
    }
}
