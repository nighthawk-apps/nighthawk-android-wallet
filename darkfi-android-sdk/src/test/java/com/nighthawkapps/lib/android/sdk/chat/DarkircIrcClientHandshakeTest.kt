package com.nighthawkapps.lib.android.sdk.chat

import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class DarkircIrcClientHandshakeTest {
    @Test
    fun connectAndJoin_completesAgainstMinimalDarkircLikeServer() =
        runBlocking {
            val ready = CountDownLatch(1)
            var port = -1
            val serverDone =
                thread(start = true, name = "fake-darkirc") {
                    ServerSocket(0).use { listener ->
                        port = listener.localPort
                        ready.countDown()
                        listener.accept().use { socket ->
                            val reader =
                                BufferedReader(
                                    InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8),
                                )
                            val writer =
                                OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)

                            fun send(line: String) {
                                writer.write(line + "\r\n")
                                writer.flush()
                            }

                            fun readLineBlocking(): String? = reader.readLine()
                            while (true) {
                                val line = readLineBlocking() ?: break
                                when {
                                    line.startsWith("PASS ") -> {}

                                    line == "CAP LS" -> {
                                        send(":darkirc CAP * LS :no-history no-autojoin")
                                    }

                                    line.startsWith("CAP REQ ") -> {
                                        send(":darkirc CAP * ACK :no-history no-autojoin")
                                    }

                                    line.startsWith("NICK ") -> {}

                                    line.startsWith("USER ") -> {}

                                    line == "CAP END" -> {
                                        send(":darkirc 001 alice :Welcome")
                                        while (true) {
                                            val next = readLineBlocking() ?: break
                                            if (next.startsWith("JOIN ")) break
                                        }
                                        break
                                    }
                                }
                            }
                        }
                    }
                }
            ready.await()
            check(port > 0)
            val client =
                DarkircIrcClient(
                    DarkircConnectionConfig(
                        host = "127.0.0.1",
                        port = port,
                        nickname = "alice",
                        routeThroughTorSocks = false,
                        connectTimeoutMs = 8000,
                    ),
                )
            client.connectAndJoin(listOf("#dev"))
            client.close()
            serverDone.join(15_000)
        }
}
