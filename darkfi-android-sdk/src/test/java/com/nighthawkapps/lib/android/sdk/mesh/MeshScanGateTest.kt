package com.nighthawkapps.lib.android.sdk.mesh

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeshScanGateTest {
    @Test
    fun advertisementWithoutMeshIdDoesNotConnect() {
        assertNull(MeshScanGate.meshIdFromAdvertisement(null))
        assertNull(MeshScanGate.meshIdFromAdvertisement(ByteArray(4)))
        assertFalse(MeshScanGate.shouldOpen(alreadyLinked = false, meshId = null))
        assertFalse(MeshScanGate.shouldAttach(alreadyLinked = true, meshId = null))
    }

    @Test
    fun eightByteIdOpensOnceThenAttaches() {
        val id = ByteArray(MeshWireCodec.SENDER_LEN) { 0x11 }
        val parsed = MeshScanGate.meshIdFromAdvertisement(id)
        assertTrue(parsed != null && parsed.contentEquals(id))
        assertTrue(MeshScanGate.shouldOpen(alreadyLinked = false, meshId = parsed))
        assertFalse(MeshScanGate.shouldOpen(alreadyLinked = true, meshId = parsed))
        assertTrue(MeshScanGate.shouldAttach(alreadyLinked = true, meshId = parsed))
    }
}
