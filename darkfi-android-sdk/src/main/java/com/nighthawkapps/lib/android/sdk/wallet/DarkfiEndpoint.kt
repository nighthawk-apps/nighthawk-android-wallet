package com.nighthawkapps.lib.android.sdk.wallet

import org.json.JSONObject

data class DarkfiEndpoint(
    val host: String,
    val port: Int,
    val isTls: Boolean,
) {
    /**
     * Lightwalletd gRPC endpoint string (parsed by Rust `normalize_lightwallet_url`).
     */
    fun toDisplayString(): String = if (isTls) "tcp+tls://$host:$port" else "tcp://$host:$port"

    companion object {
        /** Standalone `darkfi-lightwalletd` gRPC listen port. */
        const val LIGHTWALLET_GRPC_PORT: Int = 9067

        /** Upstream `darkfid` JSON-RPC (embedded node / management only — not the wallet sync URL). */
        const val DARKFID_JSON_RPC_PORT_MAINNET: Int = 8345

        /** Upstream `darkfid` JSON-RPC (embedded node / management only — not the wallet sync URL). */
        const val DARKFID_JSON_RPC_PORT_TESTNET: Int = 18345

        /** `drk_config.toml` → `[network_config."localnet"].endpoint`. */
        const val DARKFID_JSON_RPC_PORT_LOCALNET: Int = 28345

        /** `darkfid_config.toml` → `[network_config."mainnet"].management_rpc`. */
        const val DARKFID_MANAGEMENT_RPC_PORT_MAINNET: Int = 8346

        /** `darkfid_config.toml` → `[network_config."testnet"].management_rpc`. */
        const val DARKFID_MANAGEMENT_RPC_PORT_TESTNET: Int = 18346

        /** `darkfid_config.toml` → `[network_config."localnet"].management_rpc`. */
        const val DARKFID_MANAGEMENT_RPC_PORT_LOCALNET: Int = 28346

        /** Defaults to Studio testnet LWD via ngrok. Network match is enforced at sync via chain_name. */
        @Suppress("UNUSED_PARAMETER")
        fun defaultForNetwork(network: DarkfiNetwork): DarkfiEndpoint =
            when (network) {
                DarkfiNetwork.Testnet ->
                    DarkfiEndpoint(
                        host = "epidermis-sandbox-marshland.ngrok-free.dev",
                        port = 443,
                        isTls = true,
                    )
                DarkfiNetwork.Mainnet ->
                    DarkfiEndpoint(
                        host = "127.0.0.1",
                        port = LIGHTWALLET_GRPC_PORT,
                        isTls = false,
                    )
            }

        fun fromJson(obj: JSONObject): DarkfiEndpoint =
            DarkfiEndpoint(
                host = obj.getString("host"),
                port = obj.getInt("port"),
                isTls = obj.getBoolean("tls"),
            )
    }
}

fun DarkfiEndpoint.toJson(): JSONObject =
    JSONObject().apply {
        put("host", host)
        put("port", port)
        put("tls", isTls)
    }
