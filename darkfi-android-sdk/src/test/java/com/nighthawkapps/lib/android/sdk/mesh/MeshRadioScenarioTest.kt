package com.nighthawkapps.lib.android.sdk.mesh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeshRadioScenarioTest {
    @Test
    fun threePhoneConnectChatLostReconnectReset() {
        val log = MeshRadioScenario.runThreePhoneScript()
        assertEquals(
            listOf("connect a-r-g", "chat", "dag-sync", "lost", "reconnect", "reset"),
            log,
        )
    }

    @Test
    fun lostLinkDropsFrames() {
        val a = MeshRadioScenario.Phone("a")
        val r = MeshRadioScenario.Phone("r", meshOn = false)
        val frame = MeshRadioScenario.chatFrame(ByteArray(8) { 1 }, "nope")
        assertFalse(MeshRadioScenario.deliver(a, r, frame, 1L))
        assertTrue(r.inbox.isEmpty())
    }

    @Test
    fun forbiddenLwdIsDroppedOnRadio() {
        val a = MeshRadioScenario.Phone("a")
        val r = MeshRadioScenario.Phone("r")
        val frame =
            MeshWireCodec.encode(
                MeshWirePacket(
                    type = MeshWireCodec.NH_LWD_CTRL,
                    ttl = 0,
                    flags = 0,
                    timestampMs = 0L,
                    sender = ByteArray(8) { 2 },
                    recipient = null,
                    payload = "GetUnifOmrDigest\n".toByteArray(),
                ),
            ) ?: error("encode")
        assertFalse(MeshRadioScenario.deliver(a, r, frame, 1L))
        assertTrue(r.inbox.isEmpty())
    }
}
