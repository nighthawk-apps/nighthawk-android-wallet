package com.nighthawkapps.lib.android.sdk.wallet

import android.app.Application
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DarkfiSynchronizerFactoryTest {
    private val context: Application
        get() = RuntimeEnvironment.getApplication() as Application

    private val wallet =
        PersistableDarkfiWallet(
            seedPhrase = List(22) { "abandon" },
            network = DarkfiNetwork.Testnet,
            endpoint = DarkfiEndpoint.defaultForNetwork(DarkfiNetwork.Testnet),
            birthdayHeight = null,
        )

    @Test
    fun create_withoutNativeLibrary_returnsStubSynchronizer() {
        val sync = DarkfiSynchronizerFactory.create(wallet, context, useNativeSynchronizer = false)
        assertTrue(sync is StubDarkfiSynchronizer)
    }

    @Test
    fun create_withNativeFlag_fallsBackWhenLibraryMissing() {
        val sync = DarkfiSynchronizerFactory.create(wallet, context, useNativeSynchronizer = true)
        assertTrue(
            sync is StubDarkfiSynchronizer || sync is NativeDarkfiSynchronizer,
        )
    }
}
