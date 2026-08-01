package com.nighthawkapps.lib.android.sdk.wallet

/**
 * The method being used for wallet note discovery / sync.
 * Driven by the Rust [DrkLightSyncState.syncType] string from UniFFI.
 */
enum class DarkfiSyncType(
    val displayLabel: String
) {
    OMR("OMR"),
    TRIAL_DECRYPTION("Trial decryption"),
    TRIAL_DECRYPTION_FALLBACK("Trial decryption fallback"),
    MIXED_RECOVERY("Mixed recovery"),
    CATCH_UP_SYNC("Catch-up sync"),
    IDLE("Idle");

    companion object {
        /**
         * Parse the Rust sync_type string into the enum.
         * Falls back to [IDLE] for unknown values.
         */
        fun fromRustString(value: String): DarkfiSyncType =
            when (value) {
                "OMR" -> OMR
                "Trial decryption" -> TRIAL_DECRYPTION
                "Trial decryption (fallback)" -> TRIAL_DECRYPTION_FALLBACK
                "Mixed recovery" -> MIXED_RECOVERY
                "Catch-up sync" -> CATCH_UP_SYNC
                "Idle" -> IDLE
                else -> IDLE
            }
    }
}
