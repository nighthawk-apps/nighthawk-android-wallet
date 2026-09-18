package com.nighthawkapps.lib.android.sdk.wallet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DarkfiExplorerTest {
    @Test
    fun testnetTxUrl_usesTestnetHost() {
        assertEquals(
            "https://explorer.testnet.dark.fi/tx/abc123",
            DarkfiExplorer.transactionUrl("abc123", DarkfiNetwork.Testnet),
        )
    }

    @Test
    fun mainnetTxUrl_usesMainnetHost() {
        assertEquals(
            "https://explorer.dark.fi/tx/deadbeef",
            DarkfiExplorer.transactionUrl("deadbeef", DarkfiNetwork.Mainnet),
        )
    }

    @Test
    fun nullNetwork_defaultsToTestnet() {
        assertEquals(
            "https://explorer.testnet.dark.fi/tx/aa",
            DarkfiExplorer.transactionUrl("aa", null),
        )
    }

    @Test
    fun rejectsPathInjection() {
        assertNull(DarkfiExplorer.transactionUrl("../evil", DarkfiNetwork.Testnet))
        assertNull(DarkfiExplorer.transactionUrl("id?x=1", DarkfiNetwork.Testnet))
        assertNull(DarkfiExplorer.transactionUrl("id#frag", DarkfiNetwork.Testnet))
        assertNull(DarkfiExplorer.transactionUrl("", DarkfiNetwork.Testnet))
        assertNull(DarkfiExplorer.transactionUrl("   ", DarkfiNetwork.Testnet))
    }
}
