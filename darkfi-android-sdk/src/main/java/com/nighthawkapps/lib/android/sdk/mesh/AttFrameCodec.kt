package com.nighthawkapps.lib.android.sdk.mesh

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

private const val FRAME_LEN_BYTES: Int = 4
private const val ASSEMBLER_INITIAL_BYTES: Int = 256

/** ATT write payload = MTU − opcode − handle. Cap matches Rust `FRAGMENT_CHUNK`. */
object MeshAttPolicy {
    const val ATT_OVERHEAD: Int = 3
    const val DEFAULT_ATT_MTU: Int = 23

    /** Smallest ATT payload Android will write (default MTU minus opcode and handle). */
    const val MIN_ATT_PAYLOAD: Int = 20

    fun writePayloadCap(mtu: Int): Int {
        val raw = max(MIN_ATT_PAYLOAD, mtu - ATT_OVERHEAD)
        return min(MeshGattConstants.DEFAULT_FRAGMENT_BYTES, raw)
    }
}

/**
 * Length-prefixed ATT stream. Each logical mesh frame is `u32 BE len || bytes`,
 * then sliced to the current ATT payload cap.
 */
class AttFrameAssembler(
    private val maxFrame: Int =
        MeshWireCodec.MAX_PAYLOAD + MeshWireCodec.HEADER_LEN + MeshWireCodec.SENDER_LEN,
) {
    private val buf = ArrayList<Byte>(ASSEMBLER_INITIAL_BYTES)

    fun reset() {
        buf.clear()
    }

    fun ingest(chunk: ByteArray): List<ByteArray> {
        if (chunk.isEmpty()) return emptyList()
        for (b in chunk) buf.add(b)
        return drainFrames()
    }

    private fun drainFrames(): List<ByteArray> {
        val out = ArrayList<ByteArray>()
        var take = nextFrame()
        while (take is FrameTake.Ready) {
            out.add(take.bytes)
            take = nextFrame()
        }
        if (take is FrameTake.Invalid) buf.clear()
        return out
    }

    private fun nextFrame(): FrameTake {
        val len = pendingLen() ?: return FrameTake.NeedMore
        return frameForLen(len)
    }

    private fun pendingLen(): Int? {
        if (buf.size < FRAME_LEN_BYTES) return null
        return readLen()
    }

    private fun frameForLen(len: Int): FrameTake {
        if (len < 0 || len > maxFrame) return FrameTake.Invalid
        return readyOrNeedMore(len)
    }

    private fun readyOrNeedMore(len: Int): FrameTake {
        if (buf.size < FRAME_LEN_BYTES + len) return FrameTake.NeedMore
        return FrameTake.Ready(copyFrame(len))
    }

    private fun readLen(): Int {
        val header = ByteArray(FRAME_LEN_BYTES)
        for (i in header.indices) header[i] = buf[i]
        return ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN).int
    }

    private fun copyFrame(len: Int): ByteArray {
        val frame = ByteArray(len)
        for (i in 0 until len) {
            frame[i] = buf[FRAME_LEN_BYTES + i]
        }
        repeat(FRAME_LEN_BYTES + len) { buf.removeAt(0) }
        return frame
    }

    private sealed class FrameTake {
        data object NeedMore : FrameTake()

        data object Invalid : FrameTake()

        class Ready(
            val bytes: ByteArray,
        ) : FrameTake()
    }
}

object AttFrameSplitter {
    fun split(
        frame: ByteArray,
        attPayload: Int,
    ): List<ByteArray> {
        val cap = max(1, attPayload)
        val header =
            ByteBuffer
                .allocate(FRAME_LEN_BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(frame.size)
                .array()
        val stream = header + frame
        return stream.toList().chunked(cap).map { it.toByteArray() }
    }
}
