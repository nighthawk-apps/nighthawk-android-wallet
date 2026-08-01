package com.nighthawkapps.lib.android.sdk.wallet

import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.SyncMethod

/**
 * Canonical retrieval / encryption path used to discover and sync notes.
 *
 * App-side mirror of the Rust `SyncMethod` enum (shared source of truth,
 * exported to Kotlin + Swift via UniFFI). The SDK maps the generated
 * [SyncMethod] into this type so the rest of the app never depends on the
 * generated FFI type or on stringly-typed sync labels.
 */
enum class DarkfiSyncMethod(
    val displayLabel: String,
    val shortLabel: String
) {
    /** True UnifOMR (ePrint 2026/910) */
    UNIF_OMR("UnifOMR", "UnifOMR"),

    /** Client-side trial decryption of compact blocks (fallback path). */
    TRIAL_DECRYPT("Trial decryption", "Trial"),

    /** Idle, legacy, or not-yet-determined. */
    UNKNOWN("Unknown", "—");

    /** OMR-family methods provide oblivious (private) retrieval. */
    val isPrivateRetrieval: Boolean
        get() = this == UNIF_OMR

    companion object {
        /**
         * Map the UniFFI-generated [SyncMethod] into the app enum. Comparison is
         * done on the normalized variant name so it stays correct regardless of
         * the exact case UniFFI emits (e.g. `PerfOmr` vs `PERF_OMR`).
         */
        fun fromRust(method: SyncMethod): DarkfiSyncMethod =
            when (method.name.uppercase().replace("_", "")) {
                "UNIFOMR" -> UNIF_OMR
                "TRIALDECRYPT" -> TRIAL_DECRYPT
                else -> UNKNOWN
            }
    }
}
