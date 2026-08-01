package com.nighthawkapps.lib.android.sdk.chat.darkirc

import android.app.Application
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DarkircCryptoManagerTest {
    private val context: Application
        get() = RuntimeEnvironment.getApplication() as Application

    @org.junit.Before
    fun setUp() {
        val testFile = java.io.File(context.filesDir, "test_chat.preferences_pb")
        val realDataStore =
            androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
                produceFile = { testFile }
            )
        val field =
            com.nighthawkapps.lib.android.sdk.chat.DarkfiChatSecureStore::class.java
                .getDeclaredField("dataStoreInstance")
        field.isAccessible = true
        field.set(null, realDataStore)
    }

    @org.junit.After
    fun tearDown() {
        val field =
            com.nighthawkapps.lib.android.sdk.chat.DarkfiChatSecureStore::class.java
                .getDeclaredField("dataStoreInstance")
        field.isAccessible = true
        field.set(null, null)
    }

    @Test
    fun upsertChannel_roundTripsThroughLoad() {
        val secret = "11111111111111111111111111111111"
        DarkircCryptoManager.saveChannel(
            context,
            DarkircChannelCryptoConfig(channel = "#dev", secretBase58 = secret),
        )
        val channels = DarkircCryptoManager.loadChannels(context)
        assertEquals(1, channels.size)
        assertEquals("#dev", channels[0].channel)
        assertEquals(secret, channels[0].secretBase58)
        DarkircCryptoManager.removeChannel(context, "#dev")
        assertTrue(DarkircCryptoManager.loadChannels(context).isEmpty())
    }
}
