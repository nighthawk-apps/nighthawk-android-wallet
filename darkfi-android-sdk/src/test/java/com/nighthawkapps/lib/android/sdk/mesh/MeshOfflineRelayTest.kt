package com.nighthawkapps.lib.android.sdk.mesh

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MeshOfflineRelayTest {
    private val dest = ByteArray(8) { 0x11 }

    @AfterTest
    fun tearDown() {
        MeshCoordinator.clearGatewayHint()
    }

    @Test
    fun internetUpIsNone() {
        MeshCoordinator.noteGatewayAnnounce(dest, 0x03)
        val p =
            MeshOfflineRelay.plan(
                meshOn = true,
                internetUnreachable = false,
                needUnifOmr = true,
                foreground = true,
            )
        assertEquals(MeshOfflineRelay.Plan.None, p)
    }

    @Test
    fun getLightInfoIsCtrl() {
        MeshCoordinator.noteGatewayAnnounce(dest, 0x01)
        val p =
            MeshOfflineRelay.plan(
                meshOn = true,
                internetUnreachable = true,
                needUnifOmr = false,
                foreground = true,
                method = "GetLightInfo",
            )
        val ctrl = assertIs<MeshOfflineRelay.Plan.Ctrl>(p)
        assertEquals("GetLightInfo", ctrl.method)
        assertTrue(ctrl.dest.contentEquals(dest))
    }

    @Test
    fun unifOmrIsBulkJoinWhenForeground() {
        MeshCoordinator.noteGatewayAnnounce(dest, 0x03)
        val p =
            MeshOfflineRelay.plan(
                meshOn = true,
                internetUnreachable = true,
                needUnifOmr = true,
                foreground = true,
            )
        val bulk = assertIs<MeshOfflineRelay.Plan.BulkJoin>(p)
        assertTrue(bulk.dest.contentEquals(dest))
    }

    @Test
    fun unifOmrBackgroundIsNone() {
        MeshCoordinator.noteGatewayAnnounce(dest, 0x03)
        val p =
            MeshOfflineRelay.plan(
                meshOn = true,
                internetUnreachable = true,
                needUnifOmr = true,
                foreground = false,
            )
        assertEquals(MeshOfflineRelay.Plan.None, p)
    }

    @Test
    fun forbiddenMethodIsNone() {
        MeshCoordinator.noteGatewayAnnounce(dest, 0x03)
        val p =
            MeshOfflineRelay.plan(
                meshOn = true,
                internetUnreachable = true,
                needUnifOmr = false,
                foreground = true,
                method = "GetUnifOmrDigest",
            )
        assertEquals(MeshOfflineRelay.Plan.None, p)
    }

    @Test
    fun noDestIsNone() {
        MeshCoordinator.clearGatewayHint()
        val p =
            MeshOfflineRelay.plan(
                meshOn = true,
                internetUnreachable = true,
                needUnifOmr = false,
                foreground = true,
            )
        assertEquals(MeshOfflineRelay.Plan.None, p)
    }

    @Test
    fun requestBulkSkippedWithoutBulkCap() {
        MeshCoordinator.noteGatewayAnnounce(dest, 0x01)
        assertTrue(!MeshOfflineRelay.requestBulkIfNeeded(meshOn = true, foreground = true))
    }

    @Test
    fun requestBulkSkippedFromWorker() {
        MeshCoordinator.noteGatewayAnnounce(dest, 0x03)
        assertTrue(
            !MeshOfflineRelay.requestBulkIfNeeded(
                meshOn = true,
                foreground = true,
                fromBackgroundWorker = true,
            ),
        )
    }

    @Test
    fun publishChatDoesNotPutPlaintextOnMesh() {
        assertTrue(!MeshOfflineRelay.publishChat("#dev\nnick\nhello".toByteArray()))
    }
}
