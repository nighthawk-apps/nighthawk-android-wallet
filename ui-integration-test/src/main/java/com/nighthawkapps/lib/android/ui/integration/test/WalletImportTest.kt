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
class WalletImportTest {
    @get:org.junit.Rule
    val timeout =
        org.junit.rules.Timeout
            .seconds(3600)

    @Test
    fun importWalletAndSync() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            com.nighthawkapps.lib.android.sdk.wallet.DrkWalletPaths
                .root(context)
                .deleteRecursively()

            val seedPhrase =
                "mercy accuse survey carry chronic report fix oval drill hood tumble note safe canal once enforce property school easily best energy that"
                    .split(
                        " "
                    )
            var persistableWallet = PersistableDarkfiWallet.create(context, DarkfiNetwork.Testnet, WalletInitMode.ImportWallet(seedPhrase))
            persistableWallet = persistableWallet.copyWithEndpoint(DarkfiEndpoint("127.0.0.1", 9067, false))
            val synchronizer = DarkfiSynchronizerFactory.create(persistableWallet, context, true)

            val address = synchronizer.walletAddresses().privateAddresses.firstOrNull() ?: ""
            android.util.Log.e("TEST_WALLET", "Imported_Wallet_Address: $address")

            while (true) {
                synchronizer.refreshNow()
                val balance = synchronizer.confirmedBalanceAtomic.first()
                val status = synchronizer.status.first()
                android.util.Log.e("TEST_WALLET", "Sync Status: $status | Balance: $balance")
                if (balance > 0) {
                    android.util.Log.e("TEST_WALLET", "FUNDS_RECEIVED_AFTER_IMPORT!")
                    // Let's send to the requested iOS address from the prompt
                    val result =
                        synchronizer.submitTransfer(
                            recipientAddress = "fYW7HBC3m1vXyT6VCuHR8tua1sV4TZVzLQLz56wfyw7L6zLxu3UvH3aT",
                            amountDisplay = "20"
                        )
                    android.util.Log.e("TEST_WALLET", "Transfer Result: $result")
                    break
                }
                kotlinx.coroutines.delay(5000)
            }
        }
}
