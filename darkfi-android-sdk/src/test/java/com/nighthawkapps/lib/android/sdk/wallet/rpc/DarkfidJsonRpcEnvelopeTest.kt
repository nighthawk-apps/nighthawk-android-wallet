package com.nighthawkapps.lib.android.sdk.wallet.rpc

import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DarkfidJsonRpcEnvelopeTest {
    @Test
    fun requestMatchesJsonRpc20Shape() {
        val req =
            DarkfidJsonRpc.requestObject(
                id = 1,
                method = DarkfidJsonRpc.Method.PING,
                params = null,
            )
        assertEquals("2.0", req.getString("jsonrpc"))
        assertEquals(1, req.getInt("id"))
        assertEquals(DarkfidJsonRpc.Method.PING, req.getString("method"))
        assertEquals(false, req.has("params"))
    }

    @Test
    fun requestIncludesParamsArrayWhenProvided() {
        val params = JSONArray().put("x")
        val req =
            DarkfidJsonRpc.requestObject(id = 2, method = DarkfidJsonRpc.Method.BLOCKCHAIN_GET_BLOCK, params = params)
        assertEquals(params.toString(), req.getJSONArray("params").toString())
    }

    @Test
    fun resultPayloadThrowsOnErrorMember() {
        assertFailsWith<DarkfidRpcException> {
            DarkfidJsonRpc.resultPayloadOrThrow(
                JSONObject().put("error", JSONObject().put("message", "bad")),
            )
        }
    }

    @Test
    fun resultPayloadReturnsResultKey() {
        assertEquals(
            "pong",
            DarkfidJsonRpc.resultPayloadOrThrow(
                JSONObject().put("result", "pong"),
            ),
        )
    }

    @Test
    fun rpcCallerFunctionalInterfaceRuns() =
        runTest {
            val caller =
                DarkfidJsonRpcCaller {
                    JSONObject().put("result", it.getString("method"))
                }
            val ping =
                DarkfidJsonRpc.requestObject(3, DarkfidJsonRpc.Method.PING, null)
            assertEquals(DarkfidJsonRpc.Method.PING, caller.invoke(ping).getString("result"))
        }
}
