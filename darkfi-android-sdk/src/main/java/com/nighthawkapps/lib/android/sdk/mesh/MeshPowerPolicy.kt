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
    const val mayStartForegroundServiceFromBackground: Boolean = false

    private const val FOREGROUND_SCAN_ON_MS: Long = 8_000L
    private const val FOREGROUND_SCAN_OFF_MS: Long = 2_000L
    private const val PEER_SCAN_ON_MS: Long = 1_000L
    private const val PEER_SCAN_OFF_MS: Long = 29_000L
    private const val IDLE_SCAN_ON_MS: Long = 1_000L
    private const val IDLE_SCAN_OFF_MS: Long = 59_000L

    fun scanDuty(
        foreground: Boolean,
        hasPeers: Boolean,
        charging: Boolean,
        gatewayArmed: Boolean,
        alwaysOn: Boolean = true,
    ): MeshScanDuty {
        val forced = forcedDuty(charging, gatewayArmed, alwaysOn, foreground)
        if (forced != null) return forced
        return routineDuty(foreground, hasPeers)
    }

    fun gatewayReady(
        meshOn: Boolean,
        gatewayOptIn: Boolean,
        charging: Boolean,
        unmetered: Boolean,
    ): Boolean = meshOn && gatewayOptIn && charging && unmetered

    fun mayStartUnifOmrBulk(
        foreground: Boolean,
        gatewayReady: Boolean,
        fromBackgroundWorker: Boolean = false,
    ): Boolean = foreground && gatewayReady && !fromBackgroundWorker

    fun mayOriginateLwdCtrl(foreground: Boolean): Boolean = foreground

    private fun forcedDuty(
        charging: Boolean,
        gatewayArmed: Boolean,
        alwaysOn: Boolean,
        foreground: Boolean,
    ): MeshScanDuty? {
        if (charging && gatewayArmed) {
            return MeshScanDuty(scanOnMs = Long.MAX_VALUE, scanOffMs = 0)
        }
        return pausedDuty(alwaysOn, foreground)
    }

    private fun pausedDuty(
        alwaysOn: Boolean,
        foreground: Boolean,
    ): MeshScanDuty? {
        if (alwaysOn || foreground) return null
        return MeshScanDuty(scanOnMs = 0, scanOffMs = Long.MAX_VALUE)
    }

    private fun routineDuty(
        foreground: Boolean,
        hasPeers: Boolean,
    ): MeshScanDuty =
        when {
            foreground -> MeshScanDuty(FOREGROUND_SCAN_ON_MS, FOREGROUND_SCAN_OFF_MS)
            hasPeers -> MeshScanDuty(PEER_SCAN_ON_MS, PEER_SCAN_OFF_MS)
            else -> MeshScanDuty(IDLE_SCAN_ON_MS, IDLE_SCAN_OFF_MS)
        }
}
