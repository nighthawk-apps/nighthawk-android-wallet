package com.nighthawkapps.lib.android.network.util

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import javax.net.SocketFactory

data class Socks5Credentials(
    val username: String,
    val password: String,
)

/**
 * [SocketFactory] that tunnels TCP through a SOCKS5 proxy using RFC 1929 username/password
 * authentication and `ATYP=0x03` (remote hostname resolution, never local DNS).
 */
internal class Socks5AuthSocketFactory(
    private val proxyAddress: InetSocketAddress,
    private val credentialsProvider: () -> Socks5Credentials?,
    private val requireAuth: Boolean = true,
) : SocketFactory() {
    override fun createSocket(): Socket = Socks5Socket(proxyAddress, credentialsProvider, requireAuth)

    override fun createSocket(
        host: String,
        port: Int,
    ): Socket = createSocket().apply { connect(InetSocketAddress.createUnresolved(host, port)) }

    override fun createSocket(
        host: String,
        port: Int,
        localHost: InetAddress,
        localPort: Int,
    ): Socket =
        createSocket().apply {
            bind(InetSocketAddress(localHost, localPort))
            connect(InetSocketAddress.createUnresolved(host, port))
        }

    override fun createSocket(
        host: InetAddress,
        port: Int,
    ): Socket = createSocket().apply { connect(InetSocketAddress(host, port)) }

    override fun createSocket(
        address: InetAddress,
        port: Int,
        localAddress: InetAddress,
        localPort: Int,
    ): Socket =
        createSocket().apply {
            bind(InetSocketAddress(localAddress, localPort))
            connect(InetSocketAddress(address, port))
        }
}

private class Socks5Socket(
    private val proxyAddress: InetSocketAddress,
    private val credentialsProvider: () -> Socks5Credentials?,
    private val requireAuth: Boolean,
) : Socket() {
    override fun connect(endpoint: SocketAddress) {
        connect(endpoint, 0)
    }

    override fun connect(
        endpoint: SocketAddress,
        timeout: Int,
    ) {
        val inetEndpoint =
            endpoint as? InetSocketAddress
                ?: throw IOException("Unsupported SOCKS5 endpoint: $endpoint")
        val targetHost =
            inetEndpoint.hostString?.takeIf { it.isNotBlank() }
                ?: throw IOException("Missing SOCKS5 target host")
        val targetPort = inetEndpoint.port
        val resolvedProxy =
            if (proxyAddress.isUnresolved) {
                InetSocketAddress(proxyAddress.hostString, proxyAddress.port)
            } else {
                proxyAddress
            }
        super.connect(resolvedProxy, timeout)
        val previousTimeout = soTimeout
        try {
            if (timeout > 0) {
                soTimeout = timeout
            }
            performHandshake(targetHost, targetPort)
            soTimeout = previousTimeout
        } catch (e: IOException) {
            runCatching { close() }
            throw e
        } catch (e: IllegalArgumentException) {
            runCatching { close() }
            throw IOException("SOCKS5 handshake error: ${e.message}", e)
        }
    }

    private fun performHandshake(
        targetHost: String,
        targetPort: Int,
    ) {
        val out = getOutputStream()
        val inp = DataInputStream(getInputStream())
        negotiateGreetingAndAuth(inp, out)
        sendConnectAndReadReply(inp, out, targetHost, targetPort)
    }

    private fun negotiateGreetingAndAuth(
        inp: DataInputStream,
        out: java.io.OutputStream,
    ) {
        val creds = credentialsProvider()
        val greeting = buildGreeting(creds)
        out.write(greeting)
        out.flush()

        val greetingReply = ByteArray(GREETING_REPLY_LEN)
        inp.readFully(greetingReply)
        val method = greetingReply[1]
        if (greetingReply[0] != SOCKS_VERSION || method == METHOD_NO_ACCEPTABLE) {
            throw IOException("SOCKS5 proxy rejected greeting (method=${method.toInt() and BYTE_MASK})")
        }

        if (method == METHOD_USERPASS) {
            authenticateRfc1929(inp, out, creds)
        } else if (method != METHOD_NO_AUTH || requireAuth) {
            val reason =
                if (method == METHOD_NO_AUTH) {
                    "SOCKS5 proxy skipped required RFC 1929 auth"
                } else {
                    "Unsupported SOCKS5 auth method: ${method.toInt() and BYTE_MASK}"
                }
            throw IOException(reason)
        }
    }

    private fun buildGreeting(creds: Socks5Credentials?): ByteArray =
        when {
            creds != null && requireAuth -> byteArrayOf(SOCKS_VERSION, NMETHODS_ONE, METHOD_USERPASS)
            creds != null -> byteArrayOf(SOCKS_VERSION, NMETHODS_TWO, METHOD_USERPASS, METHOD_NO_AUTH)
            requireAuth -> throw IOException("SOCKS5 RFC 1929 credentials unavailable")
            else -> byteArrayOf(SOCKS_VERSION, NMETHODS_ONE, METHOD_NO_AUTH)
        }

    private fun authenticateRfc1929(
        inp: DataInputStream,
        out: java.io.OutputStream,
        creds: Socks5Credentials?,
    ) {
        val auth = creds ?: throw IOException("SOCKS5 proxy requested auth without credentials")
        val userBytes = auth.username.toByteArray(Charsets.UTF_8)
        val passBytes = auth.password.toByteArray(Charsets.UTF_8)
        requireValidFieldLen(userBytes.size)
        requireValidFieldLen(passBytes.size)

        val authReq = ByteArrayOutputStream(AUTH_REQ_OVERHEAD + userBytes.size + passBytes.size)
        authReq.write(RFC1929_VERSION)
        authReq.write(userBytes.size)
        authReq.write(userBytes)
        authReq.write(passBytes.size)
        authReq.write(passBytes)
        out.write(authReq.toByteArray())
        out.flush()

        val authReply = ByteArray(GREETING_REPLY_LEN)
        inp.readFully(authReply)
        if (authReply[0] != RFC1929_VERSION.toByte() || authReply[1] != AUTH_STATUS_OK) {
            throw IOException("SOCKS5 RFC 1929 authentication failed (${authReply[1].toInt() and BYTE_MASK})")
        }
    }

    private fun sendConnectAndReadReply(
        inp: DataInputStream,
        out: java.io.OutputStream,
        targetHost: String,
        targetPort: Int,
    ) {
        val hostBytes = targetHost.toByteArray(Charsets.UTF_8)
        requireValidFieldLen(hostBytes.size)
        val connectReq = ByteArrayOutputStream(CONNECT_REQ_OVERHEAD + hostBytes.size)
        connectReq.write(SOCKS_VERSION.toInt())
        connectReq.write(CMD_CONNECT)
        connectReq.write(RSV_ZERO)
        connectReq.write(ATYP_DOMAIN)
        connectReq.write(hostBytes.size)
        connectReq.write(hostBytes)
        connectReq.write((targetPort ushr PORT_HIGH_SHIFT) and BYTE_MASK)
        connectReq.write(targetPort and BYTE_MASK)
        out.write(connectReq.toByteArray())
        out.flush()

        val connectHeader = ByteArray(CONNECT_HEADER_LEN)
        inp.readFully(connectHeader)
        if (connectHeader[0] != SOCKS_VERSION || connectHeader[1] != AUTH_STATUS_OK) {
            throw IOException("SOCKS5 CONNECT failed (reply=${connectHeader[1].toInt() and BYTE_MASK})")
        }
        when (val atyp = connectHeader[ATYP_OFFSET].toInt() and BYTE_MASK) {
            ATYP_IPV4 -> {
                inp.readFully(ByteArray(IPV4_ADDR_PORT_LEN))
            }

            ATYP_IPV6 -> {
                inp.readFully(ByteArray(IPV6_ADDR_PORT_LEN))
            }

            ATYP_DOMAIN -> {
                val len = inp.readUnsignedByte()
                inp.readFully(ByteArray(len + PORT_LEN))
            }

            else -> {
                throw IOException("Unsupported SOCKS5 reply ATYP: $atyp")
            }
        }
    }

    private fun requireValidFieldLen(size: Int) {
        if (size <= 0 || size > MAX_FIELD_BYTES) {
            throw IOException("Invalid SOCKS5 field length: $size")
        }
    }

    private companion object {
        const val SOCKS_VERSION: Byte = 0x05
        const val RFC1929_VERSION: Int = 0x01
        const val NMETHODS_ONE: Byte = 0x01
        const val NMETHODS_TWO: Byte = 0x02
        const val METHOD_NO_AUTH: Byte = 0x00
        const val METHOD_USERPASS: Byte = 0x02
        const val METHOD_NO_ACCEPTABLE: Byte = 0xff.toByte()
        const val AUTH_STATUS_OK: Byte = 0x00
        const val CMD_CONNECT: Int = 0x01
        const val RSV_ZERO: Int = 0x00
        const val ATYP_IPV4: Int = 0x01
        const val ATYP_DOMAIN: Int = 0x03
        const val ATYP_IPV6: Int = 0x04
        const val MAX_FIELD_BYTES: Int = 255
        const val GREETING_REPLY_LEN: Int = 2
        const val AUTH_REQ_OVERHEAD: Int = 3
        const val CONNECT_REQ_OVERHEAD: Int = 7
        const val CONNECT_HEADER_LEN: Int = 4
        const val ATYP_OFFSET: Int = 3
        const val PORT_HIGH_SHIFT: Int = 8
        const val BYTE_MASK: Int = 0xff
        const val PORT_LEN: Int = 2
        const val IPV4_ADDR_PORT_LEN: Int = 6
        const val IPV6_ADDR_PORT_LEN: Int = 18
    }
}
