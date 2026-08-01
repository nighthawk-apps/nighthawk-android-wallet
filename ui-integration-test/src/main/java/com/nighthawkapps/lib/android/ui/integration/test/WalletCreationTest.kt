package com.nighthawkapps.lib.android.ui.integration.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiEndpoint
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiNetwork
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiSynchronizerFactory
import com.nighthawkapps.lib.android.sdk.wallet.PersistableDarkfiWallet
import com.nighthawkapps.lib.android.sdk.wallet.WalletInitMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WalletCreationTest {
    @Test
    fun createWalletAndPrintAddress() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            com.nighthawkapps.lib.android.sdk.wallet.DrkWalletPaths
                .root(context)
                .deleteRecursively()

            var persistableWallet = PersistableDarkfiWallet.create(context, DarkfiNetwork.Testnet, WalletInitMode.NewWallet)
            persistableWallet = persistableWallet.copyWithEndpoint(DarkfiEndpoint("127.0.0.1", 9067, false))
            val synchronizer = DarkfiSynchronizerFactory.create(persistableWallet, context, true)

            val address = synchronizer.walletAddresses().privateAddresses.firstOrNull() ?: ""
            val wordCount = persistableWallet.seedPhrase.size

            // Never log seed phrases or full addresses in instrumented tests (OWASP M1).
            android.util.Log.i(
                "TEST_WALLET",
                "Wallet created: mnemonic_words=$wordCount address_len=${address.length}",
            )
            // Loop forever so we can fund it and wait!
            while (true) {
                synchronizer.refreshNow()
                val balance = synchronizer.confirmedBalanceAtomic.first()
                val status = synchronizer.status.first()
                val syncStatusMessage = synchronizer.syncStatusMessage.first()
                val syncTypeMessage = synchronizer.syncTypeMessage.first()
                val syncType = synchronizer.syncType.first()
                val progress = synchronizer.progress.first()
                val error = synchronizer.walletErrors.first()
                android.util.Log.e(
                    "TEST_WALLET",
                    "Status: $status | Type: $syncType | Progress: $progress | Msg: $syncStatusMessage | TypeMsg: $syncTypeMessage | Error: $error"
                )
                android.util.Log.e("TEST_WALLET", "Current_Balance_Atomic: $balance")
                if (balance > 0) {
                    android.util.Log.e("TEST_WALLET", "FUNDS_RECEIVED!")
                    break
                }
                kotlinx.coroutines.delay(5000)
            }
        }
}
