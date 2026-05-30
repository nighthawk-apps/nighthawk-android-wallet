package com.nighthawkapps.lib.android.sdk.wallet.rpc

import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatPreferences
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiEndpoint
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiNetwork
import com.nighthawkapps.lib.android.sdk.wallet.PersistableDarkfiWallet
import com.nighthawkapps.lib.android.sdk.wallet.StubDarkfiSynchronizer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DarkfidLineJsonRpcCallerTest {
    private val application = RuntimeEnvironment.getApplication()

    @Test
    fun invoke_writesCrlframedLine_readsOneJsonRpcResponseLine(): Unit =
        runBlocking {
            DarkfiChatPreferences(application).routeOutboundThroughTor = false
            ServerSocket(0).use { ss ->
                val port = ss.localPort
                val serverDone =
                    thread(start = true) {
                        ss.accept().use { client ->
                            val requestLine =
                                BufferedReader(InputStreamReader(client.inputStream, UTF_8)).readLine()
                                    ?: error("no request")
                            val reqObj = JSONObject(requestLine)
                            assertEquals(DarkfidJsonRpc.Method.PING, reqObj.getString("method"))
                            client.outputStream.bufferedWriter(UTF_8).use { writer ->
                                writer.write(
                                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"pong\"}\r\n",
                                )
                                writer.flush()
                            }
                        }
                    }
                val caller =
                    DarkfidLineJsonRpcCaller(
                        application,
                        DarkfiEndpoint("127.0.0.1", port, isTls = false),
                    )
                val res =
                    caller.invoke(DarkfidJsonRpc.requestObject(1, DarkfidJsonRpc.Method.PING))
                assertEquals(
                    "pong",
                    DarkfidJsonRpc.resultPayloadOrThrow(res) as String,
                )
                serverDone.join(8_000)
            }
        }

    @Test
    fun invoke_tlsConfigured_throwsBeforeOpeningSocket(): Unit =
        runBlocking {
            DarkfiChatPreferences(application).routeOutboundThroughTor = false
            assertFailsWith<DarkfidRpcException> {
                DarkfidLineJsonRpcCaller(
                    application,
                    DarkfiEndpoint("ignored.example", 443, isTls = true),
                ).invoke(DarkfidJsonRpc.requestObject(1, DarkfidJsonRpc.Method.PING))
            }
        }

    @Test
    fun stubSynchronizer_callsRpcOnRefreshBeforeStubStateProgression(): Unit =
        runBlocking {
            val pings = AtomicInteger(0)
            val wallet =
                PersistableDarkfiWallet(
                    seedPhrase = listOf("abandon"),
                    network = DarkfiNetwork.Testnet,
                    endpoint =
                        DarkfiEndpoint(
                            "127.0.0.1",
                            DarkfiEndpoint.DARKFID_JSON_RPC_PORT_TESTNET,
                            false,
                        ),
                    birthdayHeight = null,
                )
            val rpc =
                DarkfidJsonRpcCaller {
                    pings.incrementAndGet()
                    JSONObject(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"pong\"}",
                    )
                }

            StubDarkfiSynchronizer(wallet, rpc).refreshNow()
            assertEquals(1, pings.get())
        }
}
