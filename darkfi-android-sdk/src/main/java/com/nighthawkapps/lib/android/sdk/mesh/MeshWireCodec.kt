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
        if (pkt.payload.size > MAX_PAYLOAD) return null
        if (pkt.sender.size != SENDER_LEN) return null
        val hasRx = pkt.recipient != null
        if (hasRx && pkt.recipient?.size != SENDER_LEN) return null
        val flags = if (hasRx) pkt.flags or FLAG_HAS_RECIPIENT else pkt.flags and FLAG_HAS_RECIPIENT.inv()
        val extra = if (hasRx) SENDER_LEN else 0
        val buf = ByteBuffer.allocate(HEADER_LEN + extra + pkt.payload.size).order(ByteOrder.BIG_ENDIAN)
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

    fun decode(bytes: ByteArray): MeshWirePacket? {
        if (bytes.size < HEADER_LEN) return null
        if (bytes[0] != MAGIC0 || bytes[1] != MAGIC1) return null
        if (bytes[2].toInt() and 0xFF != VERSION) return null
        val type = bytes[3].toInt() and 0xFF
        if (!isKnownType(type)) return null
        val ttl = bytes[4].toInt() and 0xFF
        val flags = bytes[5].toInt() and 0xFF
        val ts =
            ByteBuffer.wrap(bytes, 6, 8).order(ByteOrder.BIG_ENDIAN).long
        val sender = bytes.copyOfRange(14, 22)
        var off = 22
        val recipient =
            if (flags and FLAG_HAS_RECIPIENT != 0) {
                if (bytes.size < off + SENDER_LEN + 4) return null
                val rx = bytes.copyOfRange(off, off + SENDER_LEN)
                off += SENDER_LEN
                rx
            } else {
                null
            }
        if (bytes.size < off + 4) return null
        val plen = ByteBuffer.wrap(bytes, off, 4).order(ByteOrder.BIG_ENDIAN).int
        if (plen < 0 || plen > MAX_PAYLOAD) return null
        off += 4
        if (bytes.size != off + plen) return null
        return MeshWirePacket(
            type = type,
            ttl = ttl,
            flags = flags,
            timestampMs = ts,
            sender = sender,
            recipient = recipient,
            payload = bytes.copyOfRange(off, bytes.size),
        )
    }
}
