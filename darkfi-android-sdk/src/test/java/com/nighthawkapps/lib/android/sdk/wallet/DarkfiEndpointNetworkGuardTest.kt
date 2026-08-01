package com.nighthawkapps.lib.android.sdk.wallet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DarkfiEndpointNetworkGuardTest {
    @Test
    fun nonLightwalletdPort_isMismatch() {
        val result =
            DarkfiEndpointNetworkGuard.validate(
                DarkfiNetwork.Mainnet,
                DarkfiEndpoint(host = "127.0.0.1", port = 9999, isTls = false),
            )
        assertTrue(result is DarkfiEndpointNetworkGuard.Result.Mismatch)
        assertEquals(
            DarkfiEndpoint.LIGHTWALLET_GRPC_PORT,
            (result as DarkfiEndpointNetworkGuard.Result.Mismatch).expectedPort,
        )
    }

    @Test
    fun testnetWallet_withDefaultPort_isOk() {
        val result =
            DarkfiEndpointNetworkGuard.validate(
                DarkfiNetwork.Testnet,
                DarkfiEndpoint.defaultForNetwork(DarkfiNetwork.Testnet),
            )
        assertTrue(result is DarkfiEndpointNetworkGuard.Result.Ok)
    }
}
