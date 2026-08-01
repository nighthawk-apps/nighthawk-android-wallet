package com.nighthawkapps.lib.android.sdk.wallet.rpc

import android.content.Context
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiEndpoint
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiNetwork

/**
 * **`ManagementRpcHandler`** listens on a **second** URL per upstream `bin/darkfid/darkfid_config.toml`
 * (`management_rpc.rpc_listen`, e.g. mainnet **8346**, testnet **18346**).
 */
object DarkfidManagementRpc {
    fun endpointForNetwork(
        network: DarkfiNetwork,
        host: String = "127.0.0.1",
        isTls: Boolean = false,
    ): DarkfiEndpoint =
        DarkfiEndpoint(
            host = host.trim().ifBlank { "127.0.0.1" },
            port = managementPortForNetwork(network),
            isTls = isTls,
        )

    fun managementPortForNetwork(network: DarkfiNetwork): Int =
        when (network) {
            DarkfiNetwork.Mainnet -> DarkfiEndpoint.DARKFID_MANAGEMENT_RPC_PORT_MAINNET
            DarkfiNetwork.Testnet -> DarkfiEndpoint.DARKFID_MANAGEMENT_RPC_PORT_TESTNET
        }

    /** Line-framed JSON-RPC to the management listener (same wire as [DarkfidLineJsonRpcCaller]). */
    fun lineCaller(
        appContext: Context,
        endpoint: DarkfiEndpoint,
    ): DarkfidJsonRpcCaller = DarkfidLineJsonRpcCaller(appContext.applicationContext, endpoint)
}
