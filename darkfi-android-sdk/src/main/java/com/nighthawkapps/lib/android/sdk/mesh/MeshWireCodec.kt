package com.nighthawkapps.lib.android.sdk.mesh

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Sans-I/O Nighthawk Mesh envelope. Must stay byte-identical to
 * `darkfi-mobile-ffi` `mesh/packet.rs`.
 */
data class MeshWirePacket(
    val type: Int,
    val ttl: Int,
    val flags: Int,
    val timestampMs: Long,
    val sender: ByteArray,
    val recipient: ByteArray?,
    val payload: ByteArray,
)

object MeshWireCodec {
    const val MAGIC0: Byte = 0x4E
    const val MAGIC1: Byte = 0x48
    const val VERSION: Int = 1
    const val HEADER_LEN: Int = 26
    const val SENDER_LEN: Int = 8
    const val FLAG_HAS_RECIPIENT: Int = 0x01
    const val MAX_PAYLOAD: Int = 1024 * 1024

    const val NH_ANNOUNCE: Int = 0xA1
    const val NH_NOISE_HS: Int = 0xA2
    const val NH_NOISE_ENC: Int = 0xA3
    const val NH_FRAGMENT: Int = 0xA4
    const val NH_DAG_SYNC: Int = 0xA5
    const val NH_DAG_EVENT: Int = 0xA6
    const val NH_LWD_CTRL: Int = 0xA7
    const val NH_BULK_OFFER: Int = 0xA8
    const val NH_BULK_JOIN: Int = 0xA9
    const val NH_PING: Int = 0xAA
    const val NH_PONG: Int = 0xAB

    private const val BYTE_MASK: Int = 0xFF
    private const val VERSION_INDEX: Int = 2
    private const val TYPE_INDEX: Int = 3
    private const val TTL_INDEX: Int = 4
    private const val FLAGS_INDEX: Int = 5
    private const val TIMESTAMP_INDEX: Int = 6
    private const val TIMESTAMP_BYTES: Int = 8
    private const val SENDER_INDEX: Int = 14
    private const val U32_BYTES: Int = 4
    private const val AFTER_SENDER: Int = SENDER_INDEX + SENDER_LEN

    private val KNOWN_TYPES =
        intArrayOf(
            NH_ANNOUNCE,
            NH_NOISE_HS,
            NH_NOISE_ENC,
            NH_FRAGMENT,
            NH_DAG_SYNC,
            NH_DAG_EVENT,
            NH_LWD_CTRL,
            NH_BULK_OFFER,
            NH_BULK_JOIN,
            NH_PING,
            NH_PONG,
        )

    fun isKnownType(type: Int): Boolean = KNOWN_TYPES.contains(type)

    fun encode(pkt: MeshWirePacket): ByteArray? {
        if (!WireEncode.accepted(pkt)) return null
        return WireEncode.write(pkt)
    }

    fun decode(bytes: ByteArray): MeshWirePacket? = WireDecode.read(bytes)

    private object WireEncode {
        fun accepted(pkt: MeshWirePacket): Boolean {
            if (pkt.payload.size > MAX_PAYLOAD || pkt.sender.size != SENDER_LEN) return false
            val recipient = pkt.recipient
            return recipient == null || recipient.size == SENDER_LEN
        }

        fun write(pkt: MeshWirePacket): ByteArray {
            val hasRx = pkt.recipient != null
            val flags =
                if (hasRx) {
                    pkt.flags or FLAG_HAS_RECIPIENT
                } else {
                    pkt.flags and FLAG_HAS_RECIPIENT.inv()
                }
            val extra = if (hasRx) SENDER_LEN else 0
            val buf =
                ByteBuffer
                    .allocate(HEADER_LEN + extra + pkt.payload.size)
                    .order(ByteOrder.BIG_ENDIAN)
            buf.put(MAGIC0)
            buf.put(MAGIC1)
            buf.put(VERSION.toByte())
            buf.put(pkt.type.toByte())
            buf.put(pkt.ttl.toByte())
            buf.put(flags.toByte())
            buf.putLong(pkt.timestampMs)
            buf.put(pkt.sender)
            pkt.recipient?.let { buf.put(it) }
            buf.putInt(pkt.payload.size)
            buf.put(pkt.payload)
            return buf.array()
        }
    }

    private object WireDecode {
        fun read(bytes: ByteArray): MeshWirePacket? {
            val head = head(bytes) ?: return null
            return body(bytes, head)
        }

        private fun head(bytes: ByteArray): WireHead? {
            if (!preamble(bytes)) return null
            return fields(bytes)
        }

        private fun preamble(bytes: ByteArray): Boolean {
            if (bytes.size < HEADER_LEN || !magicVersion(bytes)) return false
            return isKnownType(u8(bytes, TYPE_INDEX))
        }

        private fun magicVersion(bytes: ByteArray): Boolean {
            if (bytes[0] != MAGIC0 || bytes[1] != MAGIC1) return false
            return u8(bytes, VERSION_INDEX) == VERSION
        }

        private fun fields(bytes: ByteArray): WireHead =
            WireHead(
                type = u8(bytes, TYPE_INDEX),
                ttl = u8(bytes, TTL_INDEX),
                flags = u8(bytes, FLAGS_INDEX),
                timestampMs = timestamp(bytes),
                sender = bytes.copyOfRange(SENDER_INDEX, SENDER_INDEX + SENDER_LEN),
            )

        private fun timestamp(bytes: ByteArray): Long =
            ByteBuffer
                .wrap(bytes, TIMESTAMP_INDEX, TIMESTAMP_BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .long

        private fun u8(
            bytes: ByteArray,
            index: Int,
        ): Int = bytes[index].toInt() and BYTE_MASK

        private fun body(
            bytes: ByteArray,
            head: WireHead,
        ): MeshWirePacket? {
            val located = WirePayload.locate(bytes, head.flags) ?: return null
            return MeshWirePacket(
                type = head.type,
                ttl = head.ttl,
                flags = head.flags,
                timestampMs = head.timestampMs,
                sender = head.sender,
                recipient = located.recipient,
                payload = located.bytes,
            )
        }
    }

    private object WirePayload {
        fun locate(
            bytes: ByteArray,
            flags: Int,
        ): Located? {
            val rx = recipient(bytes, flags) ?: return null
            return payloadAt(bytes, rx)
        }

        private fun recipient(
            bytes: ByteArray,
            flags: Int,
        ): Rx.Ok? {
            val rx = readRx(bytes, flags)
            if (rx is Rx.Truncated) return null
            return rx as Rx.Ok
        }

        private fun readRx(
            bytes: ByteArray,
            flags: Int,
        ): Rx {
            if (flags and FLAG_HAS_RECIPIENT == 0) return Rx.Ok(null, AFTER_SENDER)
            return presentRx(bytes)
        }

        private fun presentRx(bytes: ByteArray): Rx {
            val off = AFTER_SENDER
            if (bytes.size < off + SENDER_LEN + U32_BYTES) return Rx.Truncated
            val rx = bytes.copyOfRange(off, off + SENDER_LEN)
            return Rx.Ok(rx, off + SENDER_LEN)
        }

        private fun payloadAt(
            bytes: ByteArray,
            rx: Rx.Ok,
        ): Located? {
            if (bytes.size < rx.off + U32_BYTES) return null
            return payloadLen(bytes, rx)
        }

        private fun payloadLen(
            bytes: ByteArray,
            rx: Rx.Ok,
        ): Located? {
            val plen =
                ByteBuffer
                    .wrap(bytes, rx.off, U32_BYTES)
                    .order(ByteOrder.BIG_ENDIAN)
                    .int
            if (plen < 0 || plen > MAX_PAYLOAD) return null
            return payloadExact(bytes, rx, plen)
        }

        private fun payloadExact(
            bytes: ByteArray,
            rx: Rx.Ok,
            plen: Int,
        ): Located? {
            val start = rx.off + U32_BYTES
            if (bytes.size != start + plen) return null
            return Located(rx.recipient, bytes.copyOfRange(start, bytes.size))
        }
    }

    private class WireHead(
        val type: Int,
        val ttl: Int,
        val flags: Int,
        val timestampMs: Long,
        val sender: ByteArray,
    )

    private class Located(
        val recipient: ByteArray?,
        val bytes: ByteArray,
    )

    private sealed class Rx {
        data object Truncated : Rx()

        class Ok(
            val recipient: ByteArray?,
            val off: Int,
        ) : Rx()
    }
}
