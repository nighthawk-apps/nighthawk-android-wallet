package com.nighthawkapps.lib.android.sdk.wallet.rpc

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards [DarkfidJsonRpc.Method] against pinned upstream `bin/darkfid/src/rpc/mod.rs` match arms
 * (`docs/upstream/darkfi-revision.txt`).
 */
class DarkfidJsonRpcUpstreamParityTest {
    @Test
    fun mainRpcMethodsMatchPinnedDarkfidDefaultRpcHandler() {
        val expected =
            setOf(
                "ping",
                "clock",
                "blockchain.get_block",
                "blockchain.get_tx",
                "blockchain.get_difficulty",
                "blockchain.last_confirmed_block",
                "blockchain.best_fork_next_block_height",
                "blockchain.block_target",
                "blockchain.lookup_wasm",
                "blockchain.lookup_zkas",
                "blockchain.get_contract_state",
                "blockchain.get_contract_state_key",
                "blockchain.subscribe_blocks",
                "blockchain.subscribe_txs",
                "blockchain.subscribe_proposals",
                "tx.simulate",
                "tx.broadcast",
                "tx.pending",
                "tx.rebroadcast_pending",
                "tx.clean_pending",
                "tx.calculate_fee",
            )
        val actual =
            setOf(
                DarkfidJsonRpc.Method.PING,
                DarkfidJsonRpc.Method.CLOCK,
                DarkfidJsonRpc.Method.BLOCKCHAIN_GET_BLOCK,
                DarkfidJsonRpc.Method.BLOCKCHAIN_GET_TX,
                DarkfidJsonRpc.Method.BLOCKCHAIN_GET_DIFFICULTY,
                DarkfidJsonRpc.Method.BLOCKCHAIN_LAST_CONFIRMED_BLOCK,
                DarkfidJsonRpc.Method.BLOCKCHAIN_BEST_FORK_NEXT_BLOCK_HEIGHT,
                DarkfidJsonRpc.Method.BLOCKCHAIN_BLOCK_TARGET,
                DarkfidJsonRpc.Method.BLOCKCHAIN_LOOKUP_WASM,
                DarkfidJsonRpc.Method.BLOCKCHAIN_LOOKUP_ZKAS,
                DarkfidJsonRpc.Method.BLOCKCHAIN_GET_CONTRACT_STATE,
                DarkfidJsonRpc.Method.BLOCKCHAIN_GET_CONTRACT_STATE_KEY,
                DarkfidJsonRpc.Method.BLOCKCHAIN_SUBSCRIBE_BLOCKS,
                DarkfidJsonRpc.Method.BLOCKCHAIN_SUBSCRIBE_TXS,
                DarkfidJsonRpc.Method.BLOCKCHAIN_SUBSCRIBE_PROPOSALS,
                DarkfidJsonRpc.Method.TX_SIMULATE,
                DarkfidJsonRpc.Method.TX_BROADCAST,
                DarkfidJsonRpc.Method.TX_PENDING,
                DarkfidJsonRpc.Method.TX_REBROADCAST_PENDING,
                DarkfidJsonRpc.Method.TX_CLEAN_PENDING,
                DarkfidJsonRpc.Method.TX_CALCULATE_FEE,
            )
        assertEquals(expected, actual)
    }

    @Test
    fun managementRoutesMatchPinnedDarkfidManagementRpcHandler() {
        val expected =
            setOf(
                "ping",
                "dnet.switch",
                "dnet.subscribe_events",
                "p2p.get_info",
            )
        val actual =
            setOf(
                DarkfidJsonRpc.ManagementRoute.PING,
                DarkfidJsonRpc.ManagementRoute.DNET_SWITCH,
                DarkfidJsonRpc.ManagementRoute.DNET_SUBSCRIBE_EVENTS,
                DarkfidJsonRpc.ManagementRoute.P2P_GET_INFO,
            )
        assertEquals(expected, actual)
    }
}
