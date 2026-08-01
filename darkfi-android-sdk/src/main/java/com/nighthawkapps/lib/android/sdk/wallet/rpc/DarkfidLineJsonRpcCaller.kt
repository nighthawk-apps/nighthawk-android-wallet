package com.nighthawkapps.lib.android.sdk.wallet.rpc

import android.content.Context
import com.nighthawkapps.lib.android.sdk.net.TorOutboundSocks
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets.UTF_8

/**
 * darkfid TCP JSON-RPC: one request per line, terminated with CRLF (`write_to_stream` in upstream
 * [darkfi/src/rpc/common.rs](https://github.com/darkrenaissance/darkfi)).
 */
class DarkfidLineJsonRpcCaller(
    appContext: Context,
    endpoint: DarkfiEndpoint,
) : DarkfidJsonRpcCaller {
    private val applicationContext = appContext.applicationContext
    private val resolvedEndpoint = endpoint

    override suspend fun invoke(requestPayload: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            if (resolvedEndpoint.isTls) {
                throw DarkfidRpcException(
                    method = "invoke",
                    message = "TLS-wrapped darkfid endpoints are not supported on this caller yet.",
                )
            }
            val host = resolvedEndpoint.host.trim()
            val port = resolvedEndpoint.port
            val proxy =
                TorOutboundSocks.proxyForOutboundTcpDestination(applicationContext, host)
            val bareSocket =
                when (proxy) {
                    null -> Socket(java.net.Proxy.NO_PROXY)
                    else -> Socket(proxy)
                }
            bareSocket.use { socket ->
                socket.soTimeout = CONNECT_AND_READ_TIMEOUT_MS
                socket.connect(
                    InetSocketAddress(host, port),
                    CONNECT_AND_READ_TIMEOUT_MS,
                )
                // Do not close socket.getOutputStream() — on the JDK that closes the whole socket.
                val writer = socket.getOutputStream().bufferedWriter(UTF_8)
                writer.write(requestPayload.toString())
                writer.write(CRLF)
                writer.flush()
                val line =
                    socket.getInputStream().bufferedReader(UTF_8).use { reader ->
                        reader.readLine()
                    }
                        ?: throw DarkfidRpcException(
                            method = "invoke",
                            message = "empty JSON-RPC response from $host:$port",
                        )
                JSONObject(line.trim())
            }
        }

    companion object {
        private const val CONNECT_AND_READ_TIMEOUT_MS: Int = 30_000
        private const val CRLF = "\r\n"
    }
}
