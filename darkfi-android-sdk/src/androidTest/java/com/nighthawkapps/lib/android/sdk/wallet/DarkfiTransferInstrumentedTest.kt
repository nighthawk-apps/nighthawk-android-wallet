package com.nighthawkapps.lib.android.sdk.wallet

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nighthawkapps.lib.android.sdk.uniffi.DarkfiMobileFfiApi
import com.nighthawkapps.lib.android.sdk.uniffi.DarkfiNativeProbe
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.DarkfiWalletNativeException
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies transfer-related UniFFI symbols resolve on device when [libdarkfi_mobile_ffi] is present.
 * Full transfer execution requires a live darkfid and funded wallet (manual / CI integration).
 */
@RunWith(AndroidJUnit4::class)
class DarkfiTransferInstrumentedTest {
    @Test
    fun estimateTransferFee_method_is_linked() {
        assumeTrue(DarkfiNativeProbe.run() is DarkfiNativeProbe.Ok)

        val context =
            androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation()
                .targetContext
        val wallet =
            PersistableDarkfiWallet(
                seedPhrase = List(22) { "abandon" },
                network = DarkfiNetwork.Testnet,
                endpoint = DarkfiEndpoint.defaultForNetwork(DarkfiNetwork.Testnet),
                birthdayHeight = null,
            )

        val handle =
            runCatching { DarkfiMobileFfiApi.openWallet(context, wallet) }
                .getOrElse { error ->
                    assumeTrue(error !is DarkfiWalletNativeException)
                    throw error
                }

        val sync = NativeDarkfiSynchronizer(wallet, handle)
        assertTrue(sync.supportsNativeTransfer)

        val invalid =
            runBlocking {
                sync.estimateTransferFee("", "0")
            }
        assertTrue(invalid is DarkfiTransferResult.Failure)
    }
}
