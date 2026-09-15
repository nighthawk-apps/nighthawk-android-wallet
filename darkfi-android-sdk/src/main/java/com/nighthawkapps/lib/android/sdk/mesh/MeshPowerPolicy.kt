package com.nighthawkapps.lib.android.sdk.mesh

/**
 * Android scan duty cycle. iOS cannot set advertise intervals; this
 * policy is Android-only.
 */
data class MeshScanDuty(
    val scanOnMs: Long,
    val scanOffMs: Long,
) {
    val isContinuous: Boolean get() = scanOffMs == 0L
}

object MeshPowerPolicy {
    fun scanDuty(
        foreground: Boolean,
        hasPeers: Boolean,
        charging: Boolean,
        gatewayArmed: Boolean,
        alwaysOn: Boolean = true,
    ): MeshScanDuty {
        if (charging && gatewayArmed) {
            return MeshScanDuty(scanOnMs = Long.MAX_VALUE, scanOffMs = 0)
        }
        if (!alwaysOn && !foreground) {
            return MeshScanDuty(scanOnMs = 0, scanOffMs = Long.MAX_VALUE)
        }
        return when {
            foreground -> MeshScanDuty(8_000, 2_000)
            hasPeers -> MeshScanDuty(1_000, 29_000)
            else -> MeshScanDuty(1_000, 59_000)
        }
    }

    fun gatewayReady(
        meshOn: Boolean,
        gatewayOptIn: Boolean,
        charging: Boolean,
        unmetered: Boolean,
    ): Boolean = meshOn && gatewayOptIn && charging && unmetered

    fun mayStartForegroundServiceFromBackground(): Boolean = false

    fun mayStartUnifOmrBulk(
        foreground: Boolean,
        gatewayReady: Boolean,
        fromBackgroundWorker: Boolean = false,
    ): Boolean = foreground && gatewayReady && !fromBackgroundWorker

    fun mayOriginateLwdCtrl(foreground: Boolean): Boolean = foreground
}
