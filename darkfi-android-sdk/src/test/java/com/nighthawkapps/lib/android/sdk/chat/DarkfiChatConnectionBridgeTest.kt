package com.nighthawkapps.lib.android.sdk.chat

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DarkfiChatConnectionBridgeTest {
    @After
    fun tearDown() {
        DarkfiChatConnectionBridge.detach()
    }

    @Test
    fun queuedReconnectReplaysOnceOnNextAttach() {
        assertFalse(DarkfiChatConnectionBridge.requestReconnect())
        assertFalse(DarkfiChatConnectionBridge.requestReconnect())

        var calls = 0
        val handler: () -> Unit = { calls += 1 }
        DarkfiChatConnectionBridge.attach(handler)
        assertEquals("Multiple queued requests coalesce into a single replay", 1, calls)

        // Detach and re-attach without a new request does not replay again.
        DarkfiChatConnectionBridge.detach(handler)
        DarkfiChatConnectionBridge.attach(handler)
        assertEquals(1, calls)
    }

    @Test
    fun staleOwnerDetachDoesNotClobberNewerHandler() {
        var oldCalls = 0
        var newCalls = 0
        val oldHandler: () -> Unit = { oldCalls += 1 }
        val newHandler: () -> Unit = { newCalls += 1 }

        DarkfiChatConnectionBridge.attach(oldHandler)
        DarkfiChatConnectionBridge.attach(newHandler)

        // Old Activity onDispose runs after new Activity already attached.
        DarkfiChatConnectionBridge.detach(oldHandler)

        assertTrue(DarkfiChatConnectionBridge.requestReconnect())
        assertEquals(0, oldCalls)
        assertEquals(1, newCalls)
    }
}
