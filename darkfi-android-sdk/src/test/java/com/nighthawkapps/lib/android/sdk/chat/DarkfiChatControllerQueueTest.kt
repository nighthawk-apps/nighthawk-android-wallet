package com.nighthawkapps.lib.android.sdk.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DarkfiChatControllerQueueTest {
    @Test
    fun isReadyForImmediateSend_requiresRunningAndConnectedPhase() {
        assertTrue(isReadyForImmediateSend("running", "connected"))
        assertFalse(isReadyForImmediateSend("running", "waiting_for_peers"))
        assertFalse(isReadyForImmediateSend("running", "syncing_dag"))
        assertFalse(isReadyForImmediateSend("running", "static_sync"))
        assertFalse(isReadyForImmediateSend("starting", "connected"))
        assertFalse(isReadyForImmediateSend("stopped", "stopped"))
    }

    @Test
    fun pruneOptimisticMatch_removesQueuedBubbleEvenAfterLongOfflineDelay() {
        val queuedAtMs = 1_000_000L
        val echoedAtMs = queuedAtMs + 3_600_000L
        val existing =
            listOf(
                ChatChannelMessage(
                    eventId = "queued-123",
                    channel = "#nighthawk",
                    nick = "alice",
                    text = "hello offline",
                    timestampMs = queuedAtMs,
                    isOutgoing = true,
                ),
                ChatChannelMessage(
                    eventId = "evt-other",
                    channel = "#nighthawk",
                    nick = "bob",
                    text = "unrelated",
                    timestampMs = queuedAtMs + 500L,
                    isOutgoing = false,
                ),
            )
        val echo =
            ChatChannelMessage(
                eventId = "evt-real-1",
                channel = "#nighthawk",
                nick = "alice",
                text = "hello offline",
                timestampMs = echoedAtMs,
                isOutgoing = true,
            )

        val pruned = pruneOptimisticMatch(existing, echo)

        assertEquals(1, pruned.size)
        assertEquals("evt-other", pruned.first().eventId)
    }

    @Test
    fun pruneOptimisticMatch_removesSingleMatchingLocalBubbleAndKeepsDuplicateLaterSend() {
        val existing =
            listOf(
                ChatChannelMessage(
                    eventId = "local-1",
                    channel = "#nighthawk",
                    nick = "alice",
                    text = "ping",
                    timestampMs = 10_000L,
                    isOutgoing = true,
                ),
                ChatChannelMessage(
                    eventId = "local-2",
                    channel = "#nighthawk",
                    nick = "alice",
                    text = "ping",
                    timestampMs = 12_000L,
                    isOutgoing = true,
                ),
            )
        val echo =
            ChatChannelMessage(
                eventId = "evt-real-2",
                channel = "#nighthawk",
                nick = "alice",
                text = "ping",
                timestampMs = 11_000L,
                isOutgoing = true,
            )

        val pruned = pruneOptimisticMatch(existing, echo)

        assertEquals(1, pruned.size)
        assertEquals("local-2", pruned.first().eventId)
    }
}
