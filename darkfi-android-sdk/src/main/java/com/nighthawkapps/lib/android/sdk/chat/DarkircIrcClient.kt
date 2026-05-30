package com.nighthawkapps.lib.android.sdk.chat

import com.nighthawkapps.lib.android.spackle.Twig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.nio.charset.StandardCharsets

/**
 * Minimal IRC client for DarkIRC’s hybrid CAP/NICK/USER handshake ([darkfi]/bin/darkirc IRC server).
 *
 *
 * Basic TLS is supported when configured via `config.useTls`.
 */
internal class DarkircIrcClient(
    private val config: DarkircConnectionConfig,
) {
    private val writeLock = Any()
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: OutputStreamWriter? = null

    fun isReady(): Boolean =
        socket?.isConnected == true &&
            socket?.isClosed != true &&
            reader != null &&
            writer != null

    fun close() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
        try {
            reader?.close()
        } catch (_: Exception) {
        }
        reader = null
        try {
            writer?.close()
        } catch (_: Exception) {
        }
        writer = null
    }

    private fun sendRaw(line: String) {
        val w =
            writer ?: throw IOException("IRC writer not initialized")
        synchronized(writeLock) {
            w.write(line)
            w.write("\r\n")
            w.flush()
        }
    }

    fun sendPong(payload: String) {
        try {
            sendRaw("PONG :$payload")
        } catch (e: Exception) {
            Twig.warn(e) { "PONG failed" }
        }
    }

    fun sendPrivmsg(
        channelOrNick: String,
        text: String
    ) {
        val sanitized = DarkircPrivmsgRules.sanitizeBody(text) ?: return
        sendRaw("PRIVMSG $channelOrNick :$sanitized")
    }

    /**
     * Blocking handshake + JOIN on [Dispatchers.IO].
     */
    suspend fun connectAndJoin(joinChannels: List<String>): Unit =
        withContext(Dispatchers.IO) {
            close()
            var sock = createTcpSocket()
            sock.connect(
                InetSocketAddress(config.host, config.port),
                config.connectTimeoutMs,
            )

            if (config.useTls) {
                val factory =
                    javax.net.ssl.SSLSocketFactory
                        .getDefault() as javax.net.ssl.SSLSocketFactory
                val sslSocket = factory.createSocket(sock, config.host, config.port, true) as javax.net.ssl.SSLSocket
                sslSocket.startHandshake()
                sock = sslSocket
            }

            socket = sock
            reader = BufferedReader(InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8))
            writer = OutputStreamWriter(sock.getOutputStream(), StandardCharsets.UTF_8)

            val r = reader ?: throw IOException("reader missing")
            val localWriter = writer ?: throw IOException("writer missing")

            fun send(line: String) {
                synchronized(writeLock) {
                    localWriter.write(line)
                    localWriter.write("\r\n")
                    localWriter.flush()
                }
            }

            fun readLineHandshake(): String =
                r.readLine()
                    ?: throw IOException("DarkIRC closed connection during handshake")

            fun drainPing(line: String): String {
                var l = line
                while (DarkircWireParser.isPing(l)) {
                    send("PONG :${DarkircWireParser.pongPayload(l)}")
                    l = readLineHandshake()
                }
                return l
            }

            if (!config.ircPassword.isNullOrBlank()) {
                send("PASS :${config.ircPassword}")
            }

            send("CAP LS")
            var lsAttempts = 0
            while (lsAttempts < 48) {
                lsAttempts++
                val lsLine = drainPing(readLineHandshake())
                if (lsLine.contains(" CAP * LS ") || lsLine.contains(" CAP * LS:")) {
                    break
                }
            }
            if (lsAttempts >= 48) {
                throw IOException("CAP LS handshake timeout")
            }

            send("CAP REQ :no-history no-autojoin")
            var ackSeen = false
            var capAttempts = 0
            while (!ackSeen && capAttempts < 16) {
                capAttempts++
                val capLine = drainPing(readLineHandshake())
                if (capLine.contains(" CAP ") &&
                    (capLine.contains(" ACK ") || capLine.contains(" NAK "))
                ) {
                    ackSeen = true
                }
            }
            if (!ackSeen) {
                throw IOException("CAP REQ failed (no ACK/NAK)")
            }

            send("NICK ${config.nickname}")
            send("USER ${config.username} 0 * :${config.realname}")

            send("CAP END")
            var welcomeAttempts = 0
            while (welcomeAttempts < 256) {
                welcomeAttempts++
                val welcomeLine = drainPing(readLineHandshake())
                if (DarkircWireParser.containsWelcomeNumeric(welcomeLine)) {
                    break
                }
            }
            if (welcomeAttempts >= 256) {
                throw IOException("IRC registration timeout (missing 001 welcome)")
            }

            val chanArg =
                joinChannels
                    .map { ch ->
                        if (ch.startsWith("#")) ch else "#$ch"
                    }.joinToString(",")

            if (chanArg.isNotBlank()) {
                send("JOIN $chanArg")
            }

            Twig.info {
                "DarkIRC IRC handshake complete host=${config.host}:${config.port} nick=${config.nickname}"
            }
        }

    private fun createTcpSocket(): Socket =
        if (config.routeThroughTorSocks) {
            val proxy =
                Proxy(
                    Proxy.Type.SOCKS,
                    InetSocketAddress(config.socksHost, config.socksPort),
                )
            Socket(proxy)
        } else {
            Socket()
        }

    suspend fun runReadLoop(onPrivmsg: suspend (ChatChannelMessage) -> Unit) =
        withContext(Dispatchers.IO) {
            val r = reader ?: return@withContext
            while (coroutineContext.isActive) {
                val line =
                    try {
                        r.readLine()
                    } catch (_: Exception) {
                        null
                    }
                if (line == null) break
                when {
                    DarkircWireParser.isPing(line) -> {
                        sendPong(DarkircWireParser.pongPayload(line))
                    }

                    else -> {
                        DarkircWireParser.parsePrivmsg(line)?.let { onPrivmsg(it) }
                    }
                }
            }
        }

    companion object {
        internal const val MAX_MSG_LEN: Int = DarkircPrivmsgRules.MAX_BODY_LEN
    }
}
