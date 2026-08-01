package com.nighthawkapps.lib.android.sdk.wallet

import android.app.Application
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class StubDarkfiSynchronizerTransferTest {
    @Test
    fun stub_rejects_native_transfer() =
        runTest {
            val context: Application = RuntimeEnvironment.getApplication() as Application
            val wallet =
                PersistableDarkfiWallet(
                    seedPhrase = List(22) { "abandon" },
                    network = DarkfiNetwork.Testnet,
                    endpoint = DarkfiEndpoint.defaultForNetwork(DarkfiNetwork.Testnet),
                    birthdayHeight = null,
                )
            val sync = StubDarkfiSynchronizer(wallet, context)
            assertTrue(!sync.supportsNativeTransfer)
            val fee = sync.estimateTransferFee("drk_addr", "1.0")
            assertTrue(fee is DarkfiTransferResult.Failure)
        }
}
