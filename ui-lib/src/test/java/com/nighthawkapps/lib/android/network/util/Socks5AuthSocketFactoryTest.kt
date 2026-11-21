package com.nighthawkapps.lib.android.network.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.Executors

class Socks5AuthSocketFactoryTest {
    private data class CapturedHandshake(
        var user: String = "",
        var pass: String = "",
        var host: String = "",
        var port: Int = 0,
    )

    private fun readAndServeRfc1929Connect(
        server: ServerSocket,
        capture: CapturedHandshake,
    ) {
        server.accept().use { client ->
            val inp = DataInputStream(client.getInputStream())
            val out = client.getOutputStream()

            val ver = inp.readByte()
            val nmethods = inp.readUnsignedByte()
            val methods = ByteArray(nmethods).also { inp.readFully(it) }
            assertEquals(0x05.toByte(), ver)
            assertTrue(methods.contains(0x02.toByte()))
            out.write(byteArrayOf(0x05, 0x02))
            out.flush()

            val authVer = inp.readByte()
            val ulen = inp.readUnsignedByte()
            capture.user = ByteArray(ulen).also { inp.readFully(it) }.decodeToString()
            val plen = inp.readUnsignedByte()
            capture.pass = ByteArray(plen).also { inp.readFully(it) }.decodeToString()
            assertEquals(0x01.toByte(), authVer)
            out.write(byteArrayOf(0x01, 0x00))
            out.flush()

            val reqHeader = ByteArray(4).also { inp.readFully(it) }
            assertEquals(0x05.toByte(), reqHeader[0])
            assertEquals(0x01.toByte(), reqHeader[1])
            assertEquals(0x03.toByte(), reqHeader[3])
            val hostLen = inp.readUnsignedByte()
            capture.host = ByteArray(hostLen).also { inp.readFully(it) }.decodeToString()
            capture.port = inp.readUnsignedShort()
            out.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 127, 0, 0, 1, 0, 0))
            out.flush()

            out.write("pong".toByteArray())
            out.flush()
        }
    }

    @Test
    fun sendsRfc1929CredentialsAndRemoteDomainConnect() {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val exec = Executors.newSingleThreadExecutor()
        val captured = CapturedHandshake()
        val future = exec.submit { readAndServeRfc1929Connect(server, captured) }

        try {
            val factory =
                Socks5AuthSocketFactory(
                    proxyAddress = InetSocketAddress("127.0.0.1", server.localPort),
                    credentialsProvider = { Socks5Credentials("nym_user", "secret_pass") },
                    requireAuth = true,
                )
            val placeholder = InetAddress.getByAddress("api.coingecko.com", byteArrayOf(0, 0, 0, 0))
            factory.createSocket(placeholder, 443).use { sock ->
                val reply = sock.getInputStream().readNBytes(4).decodeToString()
                assertEquals("pong", reply)
            }
            future.get()
            assertEquals("nym_user", captured.user)
            assertEquals("secret_pass", captured.pass)
            assertEquals("api.coingecko.com", captured.host)
            assertEquals(443, captured.port)
        } finally {
            server.close()
            exec.shutdownNow()
        }
    }

    @Test(expected = IOException::class)
    fun failsWhenProxyRejectsRfc1929Credentials() {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val exec = Executors.newSingleThreadExecutor()
        exec.submit {
            server.accept().use { client ->
                val inp = DataInputStream(client.getInputStream())
                val out = client.getOutputStream()
                inp.readByte()
                val n = inp.readUnsignedByte()
                inp.readFully(ByteArray(n))
                out.write(byteArrayOf(0x05, 0x02))
                out.flush()
                inp.readByte()
                val ulen = inp.readUnsignedByte()
                inp.readFully(ByteArray(ulen))
                val plen = inp.readUnsignedByte()
                inp.readFully(ByteArray(plen))
                out.write(byteArrayOf(0x01, 0x01))
                out.flush()
            }
        }
        try {
            val factory =
                Socks5AuthSocketFactory(
                    proxyAddress = InetSocketAddress("127.0.0.1", server.localPort),
                    credentialsProvider = { Socks5Credentials("u", "bad") },
                    requireAuth = true,
                )
            val placeholder = InetAddress.getByAddress("api.coingecko.com", byteArrayOf(0, 0, 0, 0))
            factory.createSocket(placeholder, 443).close()
        } finally {
            server.close()
            exec.shutdownNow()
        }
    }
}
