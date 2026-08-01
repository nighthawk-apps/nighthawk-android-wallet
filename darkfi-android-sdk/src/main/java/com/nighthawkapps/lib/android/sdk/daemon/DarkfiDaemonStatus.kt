package com.nighthawkapps.lib.android.sdk.daemon

/**
 * Unified connection posture for DarkFi backends (wallet sync via darkfid/drk + optional embedded darkirc).
 *
 * Mirrors upstream `bin/app` wallet `connect` u8 and chat peer/DAG indicators as one UI-facing model.
 */
enum class DarkfiDaemonStatus {
    Unknown,
    Starting,
    Connecting,
    Connected,
    Reconnecting,
    Stopped,
    Error,
    Disabled,

    /** Chain reorganization detected — re-validating transactions. */
    ReorgRecovery,
}
