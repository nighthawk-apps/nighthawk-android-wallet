package com.nighthawkapps.lib.android.sdk.wallet

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DrkWalletPassStoreTest {
    private val context: Application
        get() = RuntimeEnvironment.getApplication() as Application

    @org.junit.Before
    fun setUp() {
        val testFile = java.io.File(context.filesDir, "test_pass.preferences_pb")
        val prefs =
            androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
                produceFile = { testFile }
            )
        val field = DrkWalletPassStore::class.java.getDeclaredField("dataStoreInstance")
        field.isAccessible = true
        field.set(null, prefs)
    }

    @org.junit.After
    fun tearDown() {
        val field = DrkWalletPassStore::class.java.getDeclaredField("dataStoreInstance")
        field.isAccessible = true
        field.set(null, null)
    }

    @Test
    fun getOrCreate_isStableAcrossCalls() {
        val first = DrkWalletPassStore.getOrCreate(context)
        val second = DrkWalletPassStore.getOrCreate(context)
        assertEquals(first, second)
        assertTrue(first.isNotEmpty())
    }
}
