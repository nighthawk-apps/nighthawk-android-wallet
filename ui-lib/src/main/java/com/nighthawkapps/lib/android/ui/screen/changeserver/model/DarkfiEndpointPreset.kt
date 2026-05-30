package com.nighthawkapps.lib.android.ui.screen.changeserver.model

import com.nighthawkapps.lib.android.sdk.wallet.DarkfiEndpoint
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiNetwork
import com.nighthawkapps.lib.android.sdk.wallet.toJson
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.json.JSONObject

const val CUSTOM_DARKFI_ENDPOINT_LABEL = "Custom endpoint"

data class DarkfiEndpointPreset(
    val label: String,
    val endpoint: DarkfiEndpoint,
    /** Editing placeholder before host/port are confirmed */
    val isIncompleteCustom: Boolean = false,
) {
    fun toJson(): JSONObject =
        JSONObject().apply {
            put(JSON_LABEL, label)
            put(JSON_ENDPOINT, endpoint.toJson())
            put(JSON_INCOMPLETE, isIncompleteCustom)
        }

    companion object {
        private const val JSON_LABEL = "label"
        private const val JSON_ENDPOINT = "endpoint"
        private const val JSON_INCOMPLETE = "incomplete"

        fun fromJson(jsonObject: JSONObject): DarkfiEndpointPreset? =
            try {
                DarkfiEndpointPreset(
                    label = jsonObject.getString(JSON_LABEL),
                    endpoint = DarkfiEndpoint.fromJson(jsonObject.getJSONObject(JSON_ENDPOINT)),
                    isIncompleteCustom = jsonObject.optBoolean(JSON_INCOMPLETE, false),
                )
            } catch (_: Exception) {
                null
            }
    }
}

object DarkfiEndpointCatalog {
    fun presets(network: DarkfiNetwork): ImmutableList<DarkfiEndpointPreset> =
        when (network) {
            DarkfiNetwork.Testnet -> {
                listOf(
                    DarkfiEndpointPreset(
                        "Local darkfid (loopback)",
                        DarkfiEndpoint(
                            host = "127.0.0.1",
                            port = DarkfiEndpoint.DARKFID_JSON_RPC_PORT_TESTNET,
                            isTls = false,
                        ),
                    ),
                    DarkfiEndpointPreset(
                        "Android emulator → host machine",
                        DarkfiEndpoint(
                            host = "10.0.2.2",
                            port = DarkfiEndpoint.DARKFID_JSON_RPC_PORT_TESTNET,
                            isTls = false,
                        ),
                    ),
                )
            }

            DarkfiNetwork.Mainnet -> {
                listOf(
                    DarkfiEndpointPreset(
                        "Local darkfid (loopback)",
                        DarkfiEndpoint(
                            host = "127.0.0.1",
                            port = DarkfiEndpoint.DARKFID_JSON_RPC_PORT_MAINNET,
                            isTls = false,
                        ),
                    ),
                    DarkfiEndpointPreset(
                        "Android emulator → host machine",
                        DarkfiEndpoint(
                            host = "10.0.2.2",
                            port = DarkfiEndpoint.DARKFID_JSON_RPC_PORT_MAINNET,
                            isTls = false,
                        ),
                    ),
                )
            }
        }.toImmutableList()

    fun defaultPreset(network: DarkfiNetwork): DarkfiEndpointPreset =
        DarkfiEndpointPreset(
            label = "Default (${network.networkName})",
            endpoint = DarkfiEndpoint.defaultForNetwork(network),
        )

    fun incompleteCustom(): DarkfiEndpointPreset =
        DarkfiEndpointPreset(
            label = CUSTOM_DARKFI_ENDPOINT_LABEL,
            endpoint = DarkfiEndpoint(host = "", port = 0, isTls = false),
            isIncompleteCustom = true,
        )
}

fun validateDarkfiRpcHostPort(
    host: String,
    portText: String
): Boolean {
    val port = portText.toIntOrNull() ?: return false
    return host.isNotBlank() && port in 1..65535
}
