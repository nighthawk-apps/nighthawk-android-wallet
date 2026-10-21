package com.nighthawkapps.lib.android.sdk.nym

import android.app.Application
import android.content.Context
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatPreferences
import com.nighthawkapps.lib.android.sdk.net.PrivacyNetwork
import com.nighthawkapps.lib.android.sdk.tor.TorBootstrapUiState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The native proxy is not loaded under Robolectric; these cover the preference-driven paths. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AppNymCoordinatorTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()

    @Before
    fun clearPrefs() {
        app.getSharedPreferences("darkfi_chat_transport", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun warmStart_skippedWhenWalletUsesTor() {
        DarkfiChatPreferences(app).privacyNetwork = PrivacyNetwork.Tor
        AppNymCoordinator.warmStartIfEnabled(app)
        assertEquals(TorBootstrapUiState.Disabled, AppNymCoordinator.bootstrapState.value)
    }

    @Test
    fun ensureReady_returnsNullWhenWalletIsDirect() =
        runBlocking {
            DarkfiChatPreferences(app).privacyNetwork = PrivacyNetwork.Direct
            assertNull(AppNymCoordinator.ensureReady(app))
            assertEquals(TorBootstrapUiState.Disabled, AppNymCoordinator.bootstrapState.value)
        }

    @Test
    fun ensureReady_failsClosedWhenProxyCannotStart() =
        runBlocking {
            // Default network is Nym; without the native library the start call throws.
            assertNull(AppNymCoordinator.ensureReady(app))
            assertEquals(TorBootstrapUiState.Failed, AppNymCoordinator.bootstrapState.value)
            assertEquals(PrivacyNetwork.Nym, DarkfiChatPreferences(app).privacyNetwork)
        }

    @Test
    fun disableFromSplash_switchesWalletToDirect() {
        AppNymCoordinator.disableFromSplash(app)
        assertEquals(PrivacyNetwork.Direct, DarkfiChatPreferences(app).privacyNetwork)
        assertEquals(TorBootstrapUiState.Disabled, AppNymCoordinator.bootstrapState.value)
    }
}
