package com.nighthawkapps.lib.android.sdk.chat.hud

/**
 * One outbound P2P slot in the chat network HUD (DarkFi-app density, Nighthawk chrome).
 */
data class OutboundPeerSlot(
    val slot: Int,
    val url: String?,
    val state: OutboundPeerState,
) {
    val displayUrl: String
        get() = url?.takeIf { it.isNotBlank() } ?: state.placeholderLabel
}

enum class OutboundPeerState {
    CONNECTED,
    CONNECTING,
    SLEEPING,
    ;

    val placeholderLabel: String
        get() =
            when (this) {
                CONNECTED -> "connected"
                CONNECTING -> "connecting"
                SLEEPING -> "sleeping"
            }

    companion object {
        fun fromWire(value: String): OutboundPeerState =
            when (value.lowercase()) {
                "connected" -> CONNECTED
                "connecting" -> CONNECTING
                else -> SLEEPING
            }
    }
}

enum class ChatTransport {
    TCP,
    TOR,
}
