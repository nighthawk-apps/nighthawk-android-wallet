package com.nighthawkapps.lib.android.sdk.uniffi

import com.nighthawkapps.lib.android.sdk.wallet.DarkfiEndpoint
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiNetwork
import com.nighthawkapps.lib.android.sdk.wallet.PersistableDarkfiWallet
import org.junit.Assert.assertEquals
import org.junit.Test

class DarkfiMobileFfiBootstrapTest {
    private val wallet =
        PersistableDarkfiWallet(
            seedPhrase = List(22) { "word$it" },
            network = DarkfiNetwork.Testnet,
            endpoint = DarkfiEndpoint.defaultForNetwork(DarkfiNetwork.Testnet),
            birthdayHeight = 100L,
        )

    @Test
    fun drkBootstrapSummary_mapsNetworkAndEndpoint() {
        val summary = DarkfiMobileFfiApi.drkBootstrapSummary(wallet)
        assertEquals("testnet", summary.network)
        assertEquals(22, summary.mnemonicWordCount)
        assertEquals(wallet.endpoint.toDisplayString(), summary.darkfidEndpointUrl)
    }
}
