package com.nighthawkapps.lib.android.sdk.chat

import kotlinx.coroutines.delay
import java.net.InetSocketAddress
import java.net.Socket

/**
 * A Tor SOCKS listener on loopback accepts connections once Tor has bootstrapped. We treat “TCP connect
 * succeeds” as readiness (full SOCKS5 happens later inside [java.net.Socket] via
 * [java.net.Proxy.Type.SOCKS]).
 */
internal object TorSocksReadiness {
    private const val MIN_TCP_PORT = 1
    private const val MAX_TCP_PORT = 65_535
    private const val TCP_PROBE_MIN_TIMEOUT_MS = 200
    private const val TCP_PROBE_MAX_TIMEOUT_MS = 1_500

    /**
     * Polls until **any** listed port accepts TCP on [host], trying ports in order each cycle.
     * Returns the first reachable port or null after [deadlineMs].
     */
    suspend fun awaitFirstReachablePort(
        host: String,
        ports: List<Int>,
        deadlineMs: Long,
        pollMs: Long = 500L,
    ): Int? {
        val ordered = ports.distinct().filter { it in MIN_TCP_PORT..MAX_TCP_PORT }
        val deadline = System.currentTimeMillis() + deadlineMs
        var chosen: Int? = null
        while (chosen == null && System.currentTimeMillis() < deadline) {
            for (p in ordered) {
                val timeout =
                    minOf(pollMs.toInt().coerceAtLeast(TCP_PROBE_MIN_TIMEOUT_MS), TCP_PROBE_MAX_TIMEOUT_MS)
                if (tcpReachable(host, p, connectTimeoutMs = timeout)) {
                    chosen = p
                    break
                }
            }
            if (chosen == null) {
                delay(pollMs)
            }
        }
        return chosen
            ?: ordered.firstNotNullOfOrNull { p ->
                if (tcpReachable(host, p, connectTimeoutMs = TCP_PROBE_MAX_TIMEOUT_MS)) {
                    p
                } else {
                    null
                }
            }
    }

    /**
     * Polls until [host]:[port] accepts a TCP connection or [deadlineMs] elapses.
     */
    suspend fun awaitTcpReachable(
        host: String,
        port: Int,
        deadlineMs: Long,
        pollMs: Long = 500L,
    ): Boolean {
        val deadline = System.currentTimeMillis() + deadlineMs
        while (System.currentTimeMillis() < deadline) {
            val timeout =
                minOf(pollMs.toInt().coerceAtLeast(TCP_PROBE_MIN_TIMEOUT_MS), TCP_PROBE_MAX_TIMEOUT_MS)
            if (tcpReachable(host, port, connectTimeoutMs = timeout)) {
                return true
            }
            delay(pollMs)
        }
        return tcpReachable(host, port, connectTimeoutMs = TCP_PROBE_MAX_TIMEOUT_MS)
    }

    fun tcpReachable(
        host: String,
        port: Int,
        connectTimeoutMs: Int,
    ): Boolean =
        try {
            Socket().use { sock ->
                sock.connect(InetSocketAddress(host, port), connectTimeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
}
