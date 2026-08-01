package com.nighthawkapps.lib.android.sdk.chat

import kotlinx.coroutines.runBlocking
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TorSocksReadinessTest {
    @Test
    fun tcpReachable_whenListenerAccepts() {
        ServerSocket(0).use { ss ->
            val port = ss.localPort
            val t =
                thread(start = true) {
                    ss.accept().use { }
                }
            try {
                assertTrue(TorSocksReadiness.tcpReachable("127.0.0.1", port, connectTimeoutMs = 4000))
            } finally {
                t.join(6000)
            }
        }
    }

    @Test
    fun tcpReachable_whenNothingListens() {
        val closedEphemeralPort =
            ServerSocket(0).use { ss ->
                ss.localPort
            }
        assertFalse(
            TorSocksReadiness.tcpReachable(
                "127.0.0.1",
                closedEphemeralPort,
                connectTimeoutMs = 400,
            ),
        )
    }

    @Test
    fun awaitTcpReachable_eventuallyTrue() =
        runBlocking {
            ServerSocket(0).use { ss ->
                val port = ss.localPort
                thread(start = true) {
                    Thread.sleep(400)
                    ss.accept().use { }
                }
                assertTrue(TorSocksReadiness.awaitTcpReachable("127.0.0.1", port, deadlineMs = 8000L))
            }
        }

    /**
     * First port closed, second opens shortly — [awaitFirstReachablePort] must converge without
     * requiring manual port retries in UI.
     */
    @Test
    fun awaitFirstReachablePort_skipsDeadPortThenAcceptsLivePort() =
        runBlocking {
            ServerSocket(0).use { ssLive ->
                val livePort = ssLive.localPort
                val deadPort =
                    ServerSocket(0).use { ephemeral ->
                        ephemeral.localPort
                    }
                thread(start = true, name = "tor-socks-probe-second-port") {
                    Thread.sleep(350)
                    ssLive.accept().use { }
                }
                val picked =
                    TorSocksReadiness.awaitFirstReachablePort(
                        host = "127.0.0.1",
                        ports = listOf(deadPort, livePort),
                        deadlineMs = 8000L,
                        pollMs = 120L,
                    )
                assertEquals(livePort, picked)
            }
        }

    @Test
    fun awaitFirstReachablePort_returnsNullWhenAllClosed() =
        runBlocking {
            val dead =
                ServerSocket(0).use { ss ->
                    ss.localPort
                }
            assertNull(
                TorSocksReadiness.awaitFirstReachablePort(
                    host = "127.0.0.1",
                    ports = listOf(dead),
                    deadlineMs = 600L,
                    pollMs = 100L,
                ),
            )
        }
}
