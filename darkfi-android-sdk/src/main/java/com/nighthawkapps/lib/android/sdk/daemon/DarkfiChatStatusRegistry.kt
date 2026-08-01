package com.nighthawkapps.lib.android.sdk.daemon

import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-wide chat connection state for daemon lifecycle (updated by [DarkfiChatController]). */
object DarkfiChatStatusRegistry {
    private val _state = MutableStateFlow(DarkfiChatConnectionState.Disconnected)
    val state: StateFlow<DarkfiChatConnectionState> = _state.asStateFlow()

    internal fun update(state: DarkfiChatConnectionState) {
        _state.value = state
    }
}
