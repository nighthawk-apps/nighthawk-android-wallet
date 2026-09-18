package com.nighthawkapps.lib.android.sdk.mesh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeshPowerPolicyTest {
    @Test
    fun foregroundBalancedDuty() {
        val d = MeshPowerPolicy.scanDuty(
            foreground = true,
            hasPeers = false,
            charging = false,
            gatewayArmed = false,
        )
        assertEquals(8_000, d.scanOnMs)
        assertEquals(2_000, d.scanOffMs)
        assertFalse(d.isContinuous)
    }

    @Test
    fun backgroundAloneIsSparse() {
        val d = MeshPowerPolicy.scanDuty(
            foreground = false,
            hasPeers = false,
            charging = false,
            gatewayArmed = false,
        )
        assertEquals(1_000, d.scanOnMs)
        assertEquals(59_000, d.scanOffMs)
    }

    @Test
    fun chargingGatewayIsContinuous() {
        val d = MeshPowerPolicy.scanDuty(
            foreground = false,
            hasPeers = false,
            charging = true,
            gatewayArmed = true,
        )
        assertTrue(d.isContinuous)
    }

    @Test
    fun gatewayNeedsChargingAndUnmetered() {
        assertFalse(MeshPowerPolicy.gatewayReady(true, true, false, true))
        assertFalse(MeshPowerPolicy.gatewayReady(true, true, true, false))
        assertTrue(MeshPowerPolicy.gatewayReady(true, true, true, true))
    }

    @Test
    fun cannotStartFgsFromBackground() {
        assertFalse(MeshPowerPolicy.mayStartForegroundServiceFromBackground())
    }

    @Test
    fun unifomrBulkIsForegroundOnly() {
        assertFalse(MeshPowerPolicy.mayStartUnifOmrBulk(foreground = false, gatewayReady = true))
        assertTrue(MeshPowerPolicy.mayStartUnifOmrBulk(foreground = true, gatewayReady = true))
        assertFalse(
            MeshPowerPolicy.mayStartUnifOmrBulk(
                foreground = true,
                gatewayReady = true,
                fromBackgroundWorker = true,
            ),
        )
    }

    @Test
    fun alwaysOnOffStopsBackgroundScan() {
        val d =
            MeshPowerPolicy.scanDuty(
                foreground = false,
                hasPeers = false,
                charging = false,
                gatewayArmed = false,
                alwaysOn = false,
            )
        assertEquals(0, d.scanOnMs)
    }

    @Test
    fun backgroundAlwaysOnIsSparseNotStopped() {
        val d =
            MeshPowerPolicy.scanDuty(
                foreground = false,
                hasPeers = false,
                charging = false,
                gatewayArmed = false,
                alwaysOn = true,
            )
        assertEquals(1_000, d.scanOnMs)
        assertEquals(59_000, d.scanOffMs)
    }

    @Test
    fun lwdCtrlOriginateIsForegroundOnly() {
        assertFalse(MeshPowerPolicy.mayOriginateLwdCtrl(foreground = false))
        assertTrue(MeshPowerPolicy.mayOriginateLwdCtrl(foreground = true))
    }
}
