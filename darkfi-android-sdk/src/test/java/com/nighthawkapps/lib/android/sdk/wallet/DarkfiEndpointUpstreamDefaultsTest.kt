package com.nighthawkapps.lib.android.sdk.wallet

import com.nighthawkapps.lib.android.sdk.wallet.rpc.DarkfidManagementRpc
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals

class DarkfiEndpointUpstreamDefaultsTest {
    @Test
    fun defaultMainnetMatchesDrkConfig() {
        val ep = DarkfiEndpoint.defaultForNetwork(DarkfiNetwork.Mainnet)
        assertEquals(DarkfiEndpoint.DARKFID_JSON_RPC_PORT_MAINNET, ep.port)
        assertEquals("tcp://127.0.0.1:${DarkfiEndpoint.DARKFID_JSON_RPC_PORT_MAINNET}", ep.toDisplayString())
    }

    @Test
    fun defaultTestnetMatchesDrkConfig() {
        val ep = DarkfiEndpoint.defaultForNetwork(DarkfiNetwork.Testnet)
        assertEquals(DarkfiEndpoint.DARKFID_JSON_RPC_PORT_TESTNET, ep.port)
    }

    @Test
    fun legacyPlaceholderPortsMigrateFromJson() {
        val legacyMainnet =
            JSONObject().apply {
                put("host", "127.0.0.1")
                put("port", DarkfiEndpoint.LEGACY_PLACEHOLDER_PORT_MAINNET)
                put("tls", false)
            }
        assertEquals(
            DarkfiEndpoint.DARKFID_JSON_RPC_PORT_MAINNET,
            DarkfiEndpoint.fromJson(legacyMainnet).port,
        )

        val legacyTestnet =
            JSONObject().apply {
                put("host", "10.0.2.2")
                put("port", DarkfiEndpoint.LEGACY_PLACEHOLDER_PORT_TESTNET)
                put("tls", false)
            }
        assertEquals(
            DarkfiEndpoint.DARKFID_JSON_RPC_PORT_TESTNET,
            DarkfiEndpoint.fromJson(legacyTestnet).port,
        )
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
        val ep = DarkfiEndpoint("127.0.0.1", 8345, isTls = false)
        assertEquals("tcp://127.0.0.1:8345", ep.toDisplayString())
        assertEquals("tcps://127.0.0.1:8345", ep.copy(isTls = true).toDisplayString())
    }
}
