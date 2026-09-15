package com.nighthawkapps.lib.android.sdk.mesh

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

/** ATT write payload = MTU − opcode − handle. Cap matches Rust `FRAGMENT_CHUNK`. */
object MeshAttPolicy {
    const val ATT_OVERHEAD: Int = 3
    const val DEFAULT_ATT_MTU: Int = 23

    fun writePayloadCap(mtu: Int): Int {
        val raw = max(20, mtu - ATT_OVERHEAD)
        return min(MeshGattConstants.DEFAULT_FRAGMENT_BYTES, raw)
    }
}

/**
 * Length-prefixed ATT stream. Each logical mesh frame is `u32 BE len || bytes`,
 * then sliced to the current ATT payload cap.
 */
class AttFrameAssembler(
    private val maxFrame: Int = MeshWireCodec.MAX_PAYLOAD + MeshWireCodec.HEADER_LEN + 8,
) {
    private val buf = ArrayList<Byte>(256)

    fun reset() {
        buf.clear()
    }

    fun ingest(chunk: ByteArray): List<ByteArray> {
        if (chunk.isEmpty()) return emptyList()
        for (b in chunk) buf.add(b)
        val out = ArrayList<ByteArray>()
        while (true) {
            if (buf.size < 4) break
            val len =
                ByteBuffer
                    .wrap(byteArrayOf(buf[0], buf[1], buf[2], buf[3]))
                    .order(ByteOrder.BIG_ENDIAN)
                    .int
            if (len < 0 || len > maxFrame) {
                buf.clear()
                break
            }
            if (buf.size < 4 + len) break
            val frame = ByteArray(len)
            for (i in 0 until len) {
                frame[i] = buf[4 + i]
            }
            repeat(4 + len) { buf.removeAt(0) }
            out.add(frame)
        }
        return out
    }
}

object AttFrameSplitter {
    fun split(
        frame: ByteArray,
        attPayload: Int,
    ): List<ByteArray> {
        val cap = max(1, attPayload)
        val header = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(frame.size).array()
        val stream = header + frame
        return stream.toList().chunked(cap).map { it.toByteArray() }
    }
}
