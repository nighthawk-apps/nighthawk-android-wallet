package com.nighthawkapps.lib.android.sdk.wallet.darkfid

import android.content.Context
import com.nighthawkapps.lib.android.sdk.chat.TorSocksReadiness
import com.nighthawkapps.lib.android.sdk.tor.AppTorCoordinator
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Probes **local** lightwalletd readiness only.
 *
 * Remote / `.onion` / proxied endpoints are never clearnet-probed: a
 * `Socket(Proxy.NO_PROXY)` dial would leak the real IP/DNS every ~20s and
 * break proxy-only setups. Remote reachability is left to the wallet FFI.
 */
object DarkfidConnectionProbe {
    private const val RPC_PROBE_DEADLINE_MS = 15_000L
    private const val LOOPBACK_POLL_DELAY_MS = 750L

    suspend fun isReachable(
        context: Context,
        endpoint: DarkfiEndpoint,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val host = endpoint.host.trim()
            if (!isLoopbackHost(host)) {
                android.util.Log.d("LwdProbe", "skip clearnet probe for non-loopback host")
                return@withContext true
            }
            AppTorCoordinator.ensureSocksReady(context)
            val port = endpoint.port
            android.util.Log.d("LwdProbe", "probing loopback lightwalletd")
            TorSocksReadiness.awaitTcpReachable(host, port, RPC_PROBE_DEADLINE_MS)
        }

    suspend fun awaitLoopbackRpc(
        context: Context,
        endpoint: DarkfiEndpoint,
        deadlineMs: Long = 120_000L,
    ): Boolean {
        val host = endpoint.host.trim()
        if (!isLoopbackHost(host)) {
            return true
        }
        val started = System.currentTimeMillis()
        while (System.currentTimeMillis() - started < deadlineMs) {
            if (isReachable(context, endpoint)) {
                return true
            }
            kotlinx.coroutines.delay(LOOPBACK_POLL_DELAY_MS)
        }
        return false
    }

    private fun isLoopbackHost(host: String): Boolean {
        val h = host.lowercase()
        return h == "127.0.0.1" ||
            h == "localhost" ||
            h == "::1" ||
            h == "[::1]" ||
            h == "10.0.2.2" // Android emulator host loopback
    }
}
