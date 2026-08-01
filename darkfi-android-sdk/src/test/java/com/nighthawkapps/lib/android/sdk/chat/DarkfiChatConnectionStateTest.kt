package com.nighthawkapps.lib.android.sdk.chat

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DarkfiChatConnectionStateTest {
    @Test
    fun needsIrcReconnectNudge_onlyWhenSessionUnhealthy() {
        assertTrue(DarkfiChatConnectionState.Disconnected.needsIrcReconnectNudge())
        assertTrue(DarkfiChatConnectionState.Error.needsIrcReconnectNudge())
        assertTrue(DarkfiChatConnectionState.Degraded.needsIrcReconnectNudge())

        assertFalse(DarkfiChatConnectionState.Connecting.needsIrcReconnectNudge())
        assertFalse(DarkfiChatConnectionState.ConnectedDirect.needsIrcReconnectNudge())
        assertFalse(DarkfiChatConnectionState.ConnectedViaTor.needsIrcReconnectNudge())
    }
}
