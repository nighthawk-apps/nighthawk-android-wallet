package com.nighthawkapps.lib.android.sdk.mesh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeshWireCodecTest {
    @Test
    fun goldenAnnounceMatchesRust() {
        val frame =
            byteArrayOf(
                0x4E, 0x48, 0x01, 0xA1.toByte(), 0x07, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
                0x00, 0x00, 0x00, 0x01, 0x00,
            )
        val pkt = MeshWireCodec.decode(frame)
        assertNotNull(pkt)
        assertEquals(MeshWireCodec.NH_ANNOUNCE, pkt.type)
        assertEquals(7, pkt.ttl)
        assertEquals(0L, pkt.timestampMs)
        assertTrue(pkt.sender.all { it == 0x11.toByte() })
        assertEquals(1, pkt.payload.size)
        assertEquals(0.toByte(), pkt.payload[0])
        val round = MeshWireCodec.encode(pkt)
        assertNotNull(round)
        assertTrue(round.contentEquals(frame))
    }

    @Test
    fun rejectBitchatMagicAndOpcode() {
        val frame =
            MeshWireCodec.encode(
                MeshWirePacket(
                    type = MeshWireCodec.NH_PING,
                    ttl = 7,
                    flags = 0,
                    timestampMs = 1L,
                    sender = ByteArray(8),
                    recipient = null,
                    payload = byteArrayOf(1),
                ),
            )
        assertNotNull(frame)
        frame[0] = 0x00
        assertNull(MeshWireCodec.decode(frame))
        frame[0] = MeshWireCodec.MAGIC0
        frame[3] = 0x01
        assertNull(MeshWireCodec.decode(frame))
    }

    @Test
    fun rejectOversizedPayload() {
        val huge = ByteArray(MeshWireCodec.MAX_PAYLOAD + 1)
        assertNull(
            MeshWireCodec.encode(
                MeshWirePacket(
                    type = MeshWireCodec.NH_DAG_EVENT,
                    ttl = 7,
                    flags = 0,
                    timestampMs = 0,
                    sender = ByteArray(8),
                    recipient = null,
                    payload = huge,
                ),
            ),
        )
    }
}

class MeshLwdAllowlistTest {
    @Test
    fun rejectsUnifOmrDigest() {
        assertFalse(MeshLwdAllowlist.isAllowed("GetUnifOmrDigest\n".toByteArray()))
        assertFalse(MeshLwdAllowlist.isAllowed("FetchPirBatch\n".toByteArray()))
        assertTrue(MeshLwdAllowlist.isAllowed("GetLightInfo\n".toByteArray()))
        assertTrue(MeshLwdAllowlist.isAllowed("SendTransaction\n".toByteArray()))
    }

    @Test
    fun rejectsOversizeCtrl() {
        val body = ByteArray(MeshLwdAllowlist.MAX_CTRL_BYTES + 8) { 'A'.code.toByte() }
        assertFalse(MeshLwdAllowlist.isAllowed(body))
    }
}

class AttFrameCodecTest {
    @Test
    fun splitAndReassemble() {
        val frame = ByteArray(800) { it.toByte() }
        val chunks = AttFrameSplitter.split(frame, 20)
        assertTrue(chunks.size > 1)
        val asm = AttFrameAssembler()
        val out = ArrayList<ByteArray>()
        for (c in chunks) out.addAll(asm.ingest(c))
        assertEquals(1, out.size)
        assertTrue(out[0].contentEquals(frame))
    }

    @Test
    fun attCapNeverExceedsFragmentChunk() {
        assertEquals(20, MeshAttPolicy.writePayloadCap(23))
        assertEquals(MeshGattConstants.DEFAULT_FRAGMENT_BYTES, MeshAttPolicy.writePayloadCap(517))
        assertEquals(MeshGattConstants.DEFAULT_FRAGMENT_BYTES, MeshAttPolicy.writePayloadCap(2000))
    }

    @Test
    fun oversizedLengthPrefixIsDropped() {
        val bad = byteArrayOf(0x7F, 0x00, 0x00, 0x00, 1)
        assertTrue(AttFrameAssembler().ingest(bad).isEmpty())
    }
}

class MeshPeerTableTest {
    @Test
    fun evictsOldestAtBudget() {
        val t = MeshPeerTable(maxPeers = 2)
        assertNull(t.admit("a", 1))
        assertNull(t.admit("b", 2))
        assertEquals("a", t.admit("c", 3))
        assertFalse(t.contains("a"))
        assertTrue(t.contains("b"))
        assertTrue(t.contains("c"))
    }
}

class MeshEnvironmentTest {
    @Test
    fun chargingRequiresPluggedExtra() {
        assertFalse(MeshEnvironment.isCharging(null))
    }
}

class MeshDagBridgeTest {
    @Test
    fun ignoresPlaintextDagEvent() {
        val bridge = MeshDagBridge()
        val pkt =
            MeshWirePacket(
                type = MeshWireCodec.NH_DAG_EVENT,
                ttl = 7,
                flags = 0,
                timestampMs = 0,
                sender = ByteArray(8) { 2 },
                recipient = null,
                payload = ByteArray(64) { 3 },
            )
        assertEquals(MeshDagAdmit.Ignored, bridge.consider(pkt))
        assertEquals(0, bridge.admittedCount)
    }

    @Test
    fun rejectsBlobOverReassemblyCap() {
        val bridge = MeshDagBridge()
        val pkt =
            MeshWirePacket(
                type = MeshWireCodec.NH_NOISE_ENC,
                ttl = 0,
                flags = 0,
                timestampMs = 0,
                sender = ByteArray(8) { 2 },
                recipient = ByteArray(8) { 3 },
                payload = ByteArray(MeshDagBridge.MAX_MESH_EVENT_BLOB + 1) { 1 },
            )
        assertEquals(MeshDagAdmit.RejectedOversized, bridge.consider(pkt))
        assertEquals(0, bridge.admittedCount)
    }
}

class MeshInboxDropsForbiddenLwd {
    @Test
    fun unifomrNeverQueued() {
        val inbox = MeshFrameInbox()
        val pkt =
            MeshWirePacket(
                type = MeshWireCodec.NH_LWD_CTRL,
                ttl = 0,
                flags = 0,
                timestampMs = 0,
                sender = ByteArray(8) { 1 },
                recipient = null,
                payload = "GetUnifOmrDigest\n".toByteArray(),
            )
        val frame = MeshWireCodec.encode(pkt)
        assertNotNull(frame)
        inbox.onLinkFrame(frame)
        assertEquals(0, inbox.size)
    }

    @Test
    fun rejectDoesNotIngest() {
        val inbox = MeshFrameInbox()
        val pkt =
            MeshWirePacket(
                type = MeshWireCodec.NH_LWD_CTRL,
                ttl = 0,
                flags = 0,
                timestampMs = 0,
                sender = ByteArray(8) { 1 },
                recipient = null,
                payload = "GetUnifOmrDigest\n".toByteArray(),
            )
        val frame = MeshWireCodec.encode(pkt)
        assertNotNull(frame)
        assertFalse(inbox.shouldIngest(frame!!))
    }

    @Test
    fun plaintextDagIsNotIngested() {
        val inbox = MeshFrameInbox()
        val pkt =
            MeshWirePacket(
                type = MeshWireCodec.NH_DAG_EVENT,
                ttl = 7,
                flags = 0,
                timestampMs = 0,
                sender = ByteArray(8) { 1 },
                recipient = null,
                payload = "channel\nnick\nbody".toByteArray(),
            )
        val frame = MeshWireCodec.encode(pkt)
        assertNotNull(frame)
        assertFalse(inbox.shouldIngest(frame!!))
    }
}
