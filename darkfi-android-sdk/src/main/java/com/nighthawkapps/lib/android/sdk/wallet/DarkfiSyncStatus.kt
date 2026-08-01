package com.nighthawkapps.lib.android.sdk.wallet

enum class DarkfiSyncStatus {
    DISCONNECTED,
    CONNECTING,
    SYNCING,
    SYNCED,
    RETRYING,
    DEGRADED,
    ERROR,
    STOPPED,

    /** Chain reorganization detected — transactions are being re-validated. */
    REORG_DETECTED,
}
