package com.nighthawkapps.lib.android.sdk.wallet

/**
 * Ensures the wallet sync endpoint is standalone lightwalletd (:9067).
 * Network (testnet vs mainnet) is validated at sync via `GetLightInfo.chain_name`.
 */
object DarkfiEndpointNetworkGuard {
    sealed interface Result {
        data object Ok : Result

        data class Mismatch(
            val walletNetwork: DarkfiNetwork,
            val endpointPort: Int,
            val expectedPort: Int,
        ) : Result
    }

    fun validate(
        walletNetwork: DarkfiNetwork,
        endpoint: DarkfiEndpoint,
    ): Result {
        val expectedPort = DarkfiEndpoint.LIGHTWALLET_GRPC_PORT
        return if (endpoint.port == expectedPort) {
            Result.Ok
        } else {
            Result.Mismatch(walletNetwork, endpoint.port, expectedPort)
        }
    }

    fun mismatchMessage(result: Result.Mismatch): String =
        buildString {
            append("Server endpoint uses port ")
            append(result.endpointPort)
            append(" but this wallet expects lightwalletd on ")
            append(result.expectedPort)
            append(". Open Change server and pick Local lightwalletd.")
        }
}
