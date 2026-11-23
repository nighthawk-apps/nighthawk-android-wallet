package com.nighthawkapps.lib.android.sdk.mesh

/**
 * BLE scan-failure policy. Android keeps a scanner registration after
 * [android.bluetooth.le.ScanCallback.onScanFailed], so the next start reports
 * already-started unless that registration is stopped first. Feature-unsupported
 * is permanent. Other codes retry with a capped backoff.
 *
 * Adapted from BitChat's scan recovery. GATT UUIDs and packet bytes stay Nighthawk.
 */
internal object MeshScanRecovery {
    const val ALREADY_STARTED: Int = 1
    const val REGISTRATION_FAILED: Int = 2
    const val INTERNAL_ERROR: Int = 3
    const val FEATURE_UNSUPPORTED: Int = 4
    const val OUT_OF_HARDWARE_RESOURCES: Int = 5
    const val SCANNING_TOO_FREQUENTLY: Int = 6

    const val BASE_MS: Long = 3_000L
    const val MAX_MS: Long = 30_000L

    fun shouldRetry(errorCode: Int): Boolean = errorCode != FEATURE_UNSUPPORTED

    fun delayMs(
        errorCode: Int,
        attempt: Int,
    ): Long {
        val base =
            when (errorCode) {
                OUT_OF_HARDWARE_RESOURCES -> BASE_MS * 3
                SCANNING_TOO_FREQUENTLY -> 10_000L
                else -> BASE_MS
            }
        return (base * attempt.coerceAtLeast(1)).coerceAtMost(MAX_MS)
    }
}
