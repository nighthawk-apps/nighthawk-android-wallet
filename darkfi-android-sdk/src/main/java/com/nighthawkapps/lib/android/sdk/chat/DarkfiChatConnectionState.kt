package com.nighthawkapps.lib.android.sdk.chat

/**
 * High-level chat/network posture for the Kotlin UI.
 *
 * Parity target: DarkFi `bin/darkirc` EventGraph/P2p lifecycle once JNI lands.
 * Today states reflect the Kotlin IRC session (loopback/SOCKS) plus DNS probes for lilith seeds.
 */
enum class DarkfiChatConnectionState {
    Disconnected,
    Connecting,
    ConnectedDirect,
    ConnectedViaTor,
    Degraded,
    Error,
    ;

    /** Whether periodic daemon maintenance should invoke [DarkfiChatController.connectOrRetry]. */
    fun needsIrcReconnectNudge(): Boolean =
        when (this) {
            Disconnected,
            Error,
            Degraded,
            -> true

            Connecting,
            ConnectedDirect,
            ConnectedViaTor,
            -> false
        }
}
