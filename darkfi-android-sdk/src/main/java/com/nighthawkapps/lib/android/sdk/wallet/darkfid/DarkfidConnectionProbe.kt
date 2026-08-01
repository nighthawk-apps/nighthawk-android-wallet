package com.nighthawkapps.lib.android.sdk.wallet.darkfid

import android.content.Context
import com.nighthawkapps.lib.android.sdk.chat.TorSocksReadiness
import com.nighthawkapps.lib.android.sdk.tor.AppTorCoordinator
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Probes standalone **lightwalletd** reachability (TCP on gRPC port 9067).
 */
object DarkfidConnectionProbe {
    private const val RPC_PROBE_DEADLINE_MS = 15_000L
    private const val LOOPBACK_POLL_DELAY_MS = 750L

    suspend fun isReachable(
        context: Context,
        endpoint: DarkfiEndpoint,
    ): Boolean =
        withContext(Dispatchers.IO) {
            AppTorCoordinator.ensureSocksReady(context)
            val host = endpoint.host.trim()
            val port = endpoint.port
            android.util.Log.d("LwdProbe", "probing lightwalletd $host:$port ...")
            val ok =
                TorSocksReadiness.awaitTcpReachable(host, port, RPC_PROBE_DEADLINE_MS)
            if (!ok) {
                android.util.Log.w(
                    "LwdProbe",
                    "TCP not reachable $host:$port after ${RPC_PROBE_DEADLINE_MS}ms",
                )
            } else {
                android.util.Log.d("LwdProbe", "TCP reachable")
            }
            ok
        }

    suspend fun awaitLoopbackRpc(
        context: Context,
        endpoint: DarkfiEndpoint,
        deadlineMs: Long = 120_000L,
    ): Boolean {
        val started = System.currentTimeMillis()
        while (System.currentTimeMillis() - started < deadlineMs) {
            if (isReachable(context, endpoint)) {
                return true
            }
            kotlinx.coroutines.delay(LOOPBACK_POLL_DELAY_MS)
        }
        return false
    }
}
