package com.nighthawkapps.lib.android.sdk.wallet.rpc

import org.json.JSONArray
import org.json.JSONObject

/**
 * **`darkfid`** main JSON-RPC (`DefaultRpcHandler` in upstream **`bin/darkfid/src/rpc/mod.rs`**).
 * Node RPC only — not **`drk`** **`wallet.db`** operations.
 *
 * Management RPC (**`ManagementRpcHandler`**, **`bin/darkfid/src/rpc/management.rs`**) listens on a **second**
 * URL (**`Darkfid::start`**, **`bin/darkfid/src/lib.rs`**). See [ManagementRoute].
 *
 * Tx methods take base64 payloads per **`bin/darkfid/src/rpc/tx.rs`** RPCAPI blocks.
 *
 * Bump **`docs/upstream/darkfi-revision.txt`** when syncing; parity matrix in **`docs/darkfi-integration.md`**.
 */
object DarkfidJsonRpc {
    object Method {
        const val PING = "ping"
        const val CLOCK = "clock"

        const val BLOCKCHAIN_GET_BLOCK = "blockchain.get_block"
        const val BLOCKCHAIN_GET_TX = "blockchain.get_tx"
        const val BLOCKCHAIN_GET_DIFFICULTY = "blockchain.get_difficulty"
        const val BLOCKCHAIN_LAST_CONFIRMED_BLOCK = "blockchain.last_confirmed_block"
        const val BLOCKCHAIN_BEST_FORK_NEXT_BLOCK_HEIGHT =
            "blockchain.best_fork_next_block_height"
        const val BLOCKCHAIN_BLOCK_TARGET = "blockchain.block_target"
        const val BLOCKCHAIN_LOOKUP_WASM = "blockchain.lookup_wasm"
        const val BLOCKCHAIN_LOOKUP_ZKAS = "blockchain.lookup_zkas"
        const val BLOCKCHAIN_GET_CONTRACT_STATE = "blockchain.get_contract_state"
        const val BLOCKCHAIN_GET_CONTRACT_STATE_KEY =
            "blockchain.get_contract_state_key"
        const val BLOCKCHAIN_SUBSCRIBE_BLOCKS = "blockchain.subscribe_blocks"
        const val BLOCKCHAIN_SUBSCRIBE_TXS = "blockchain.subscribe_txs"
        const val BLOCKCHAIN_SUBSCRIBE_PROPOSALS =
            "blockchain.subscribe_proposals"

        const val TX_SIMULATE = "tx.simulate"
        const val TX_BROADCAST = "tx.broadcast"
        const val TX_PENDING = "tx.pending"
        const val TX_REBROADCAST_PENDING = "tx.rebroadcast_pending"
        const val TX_CLEAN_PENDING = "tx.clean_pending"
        const val TX_CALCULATE_FEE = "tx.calculate_fee"
    }

    /**
     * **`ManagementRpcHandler`** only — alternate darkfid **`listen`** URL (`management_rpc_settings`).
     */
    object ManagementRoute {
        const val PING = "ping"
        const val DNET_SWITCH = "dnet.switch"
        const val DNET_SUBSCRIBE_EVENTS = "dnet.subscribe_events"
        const val P2P_GET_INFO = "p2p.get_info"
    }

    /** JSON-RPC 2.0 request object (serialized for TCP/HTTP transports). */
    fun requestObject(
        id: Int,
        method: String,
        params: JSONArray? = null,
    ): JSONObject =
        JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            if (params != null) {
                put("params", params)
            }
        }

    /** Best-effort parse of a JSON-RPC response `result` payload (opaque until UniFFI maps types). */
    fun resultPayloadOrThrow(responseBody: JSONObject): Any? {
        if (responseBody.has("error") && responseBody.opt("error") != JSONObject.NULL) {
            val err = responseBody.optJSONObject("error") ?: JSONObject()
            val msg = err.optString("message", err.toString())
            throw DarkfidRpcException(method = "(response)", message = msg)
        }
        return responseBody.opt("result")
    }
}

class DarkfidRpcException(
    val method: String,
    message: String,
) : RuntimeException("$method: $message")

/**
 * Implemented by JNI/UniFFI or by a JVM socket adapter when wired; stubs keep UI tests offline.
 */
fun interface DarkfidJsonRpcCaller {
    suspend fun invoke(requestPayload: JSONObject): JSONObject
}
