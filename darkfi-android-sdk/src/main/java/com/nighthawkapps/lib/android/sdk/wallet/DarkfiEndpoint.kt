package com.nighthawkapps.lib.android.sdk.wallet

import org.json.JSONObject

data class DarkfiEndpoint(
    val host: String,
    val port: Int,
    val isTls: Boolean,
) {
    /**
     * Upstream defaults use **TCP** JSON-RPC (`bin/drk/drk_config.toml` → `[network_config].endpoint`).
     * `isTls` is reserved if darkfid exposes TLS-wrapped transports later.
     */
    fun toDisplayString(): String {
        val scheme =
            when {
                isTls -> "tcps"
                else -> "tcp"
            }
        return "$scheme://$host:$port"
    }

    /**
     * Older app builds used 26660 (mainnet presets) and 26670 (testnet). Upstream `drk` uses darkfid
     * JSON-RPC on 8345 / 18345 instead (`bin/drk/drk_config.toml`).
     */
    fun withMigratedLegacyPlaceholderPorts(): DarkfiEndpoint =
        when (port) {
            LEGACY_PLACEHOLDER_PORT_MAINNET -> copy(port = DARKFID_JSON_RPC_PORT_MAINNET)
            LEGACY_PLACEHOLDER_PORT_TESTNET -> copy(port = DARKFID_JSON_RPC_PORT_TESTNET)
            else -> this
        }

    companion object {
        /** `drk_config.toml` → `[network_config."mainnet"].endpoint`. */
        const val DARKFID_JSON_RPC_PORT_MAINNET: Int = 8345

        /** `drk_config.toml` → `[network_config."testnet"].endpoint`. */
        const val DARKFID_JSON_RPC_PORT_TESTNET: Int = 18345

        /** `drk_config.toml` → `[network_config."localnet"].endpoint` — not surfaced in [DarkfiNetwork] yet. */
        const val DARKFID_JSON_RPC_PORT_LOCALNET: Int = 28345

        /** `darkfid_config.toml` → `[network_config."mainnet"].management_rpc`. */
        const val DARKFID_MANAGEMENT_RPC_PORT_MAINNET: Int = 8346

        /** `darkfid_config.toml` → `[network_config."testnet"].management_rpc`. */
        const val DARKFID_MANAGEMENT_RPC_PORT_TESTNET: Int = 18346

        /** `darkfid_config.toml` → `[network_config."localnet"].management_rpc`. */
        const val DARKFID_MANAGEMENT_RPC_PORT_LOCALNET: Int = 28346

        /** Ports used before aligning with upstream drk; migrated when loading persisted wallets. */
        const val LEGACY_PLACEHOLDER_PORT_MAINNET: Int = 26660

        /** Ports used before aligning with upstream drk; migrated when loading persisted wallets. */
        const val LEGACY_PLACEHOLDER_PORT_TESTNET: Int = 26670

        fun defaultForNetwork(network: DarkfiNetwork): DarkfiEndpoint =
            when (network) {
                DarkfiNetwork.Mainnet -> {
                    DarkfiEndpoint(
                        host = "127.0.0.1",
                        port = DARKFID_JSON_RPC_PORT_MAINNET,
                        isTls = false,
                    )
                }

                DarkfiNetwork.Testnet -> {
                    DarkfiEndpoint(
                        host = "127.0.0.1",
                        port = DARKFID_JSON_RPC_PORT_TESTNET,
                        isTls = false,
                    )
                }
            }

        fun fromJson(obj: JSONObject): DarkfiEndpoint =
            DarkfiEndpoint(
                host = obj.getString("host"),
                port = obj.getInt("port"),
                isTls = obj.getBoolean("tls"),
            ).withMigratedLegacyPlaceholderPorts()
    }
}

fun DarkfiEndpoint.toJson(): JSONObject =
    JSONObject().apply {
        put("host", host)
        put("port", port)
        put("tls", isTls)
    }
