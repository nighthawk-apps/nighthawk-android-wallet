package com.nighthawkapps.lib.android.sdk.wallet

import com.nighthawkapps.lib.android.sdk.wallet.rpc.DarkfidManagementRpc
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals

class DarkfiEndpointUpstreamDefaultsTest {
    @Test
    fun defaultMainnetIsStandaloneLightwalletd() {
        val ep = DarkfiEndpoint.defaultForNetwork(DarkfiNetwork.Mainnet)
        assertEquals(DarkfiEndpoint.LIGHTWALLET_GRPC_PORT, ep.port)
        assertEquals("tcp://127.0.0.1:${DarkfiEndpoint.LIGHTWALLET_GRPC_PORT}", ep.toDisplayString())
    }

    @Test
    fun defaultTestnetIsStudioNgrokHttps() {
        val ep = DarkfiEndpoint.defaultForNetwork(DarkfiNetwork.Testnet)
        assertEquals(443, ep.port)
        assertEquals(true, ep.isTls)
        assertEquals(
            "tcp+tls://epidermis-sandbox-marshland.ngrok-free.dev:443",
            ep.toDisplayString(),
        )
    }

    @Test
    fun fromJsonPreservesPort() {
        val obj =
            JSONObject().apply {
                put("host", "10.0.2.2")
                put("port", DarkfiEndpoint.LIGHTWALLET_GRPC_PORT)
                put("tls", false)
            }
        assertEquals(DarkfiEndpoint.LIGHTWALLET_GRPC_PORT, DarkfiEndpoint.fromJson(obj).port)
        assertEquals("10.0.2.2", DarkfiEndpoint.fromJson(obj).host)
    }

    @Test
    fun managementPortsMatchDarkfidConfigToml() {
        assertEquals(8346, DarkfiEndpoint.DARKFID_MANAGEMENT_RPC_PORT_MAINNET)
        assertEquals(18346, DarkfiEndpoint.DARKFID_MANAGEMENT_RPC_PORT_TESTNET)
        assertEquals(
            8346,
            DarkfidManagementRpc.managementPortForNetwork(DarkfiNetwork.Mainnet),
        )
        assertEquals(
            "tcp://127.0.0.1:8346",
            DarkfidManagementRpc.endpointForNetwork(DarkfiNetwork.Mainnet).toDisplayString(),
        )
    }

    @Test
    fun displayUsesTcpSchemeByDefault() {
        val ep = DarkfiEndpoint("127.0.0.1", 9067, isTls = false)
        assertEquals("tcp://127.0.0.1:9067", ep.toDisplayString())
        assertEquals("tcp+tls://127.0.0.1:9067", ep.copy(isTls = true).toDisplayString())
    }
}
