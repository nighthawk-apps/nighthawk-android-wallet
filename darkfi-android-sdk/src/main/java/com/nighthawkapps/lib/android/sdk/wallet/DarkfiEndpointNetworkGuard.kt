package com.nighthawkapps.lib.android.sdk.wallet

/**
 * Ensures the wallet sync endpoint is standalone lightwalletd
 * (:9067 cleartext / :9068 alt / :443 TLS terminator e.g. ngrok).
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

    /** Ports accepted for lightwalletd gRPC (matches iOS / desktop allow-lists). */
    val allowedPorts: Set<Int> =
        setOf(
            DarkfiEndpoint.LIGHTWALLET_GRPC_PORT,
            9068,
            443,
        )

    fun validate(
        walletNetwork: DarkfiNetwork,
        endpoint: DarkfiEndpoint,
    ): Result =
        if (endpoint.port in allowedPorts) {
            Result.Ok
        } else {
            Result.Mismatch(
                walletNetwork,
                endpoint.port,
                DarkfiEndpoint.LIGHTWALLET_GRPC_PORT,
            )
        }

    fun mismatchMessage(result: Result.Mismatch): String =
        buildString {
            append("Server endpoint uses port ")
            append(result.endpointPort)
            append(" but this wallet expects lightwalletd on ")
            append(allowedPorts.sorted().joinToString("/"))
            append(". Open Change server and pick a lightwalletd endpoint.")
        }
}
