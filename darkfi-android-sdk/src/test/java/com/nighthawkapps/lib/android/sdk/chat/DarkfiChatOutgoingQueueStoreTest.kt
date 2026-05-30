package com.nighthawkapps.lib.android.sdk.chat

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DarkfiChatOutgoingQueueStoreTest {
    private val tempFile = File.createTempFile("darkfi-chat-q-", ".json")

    @AfterTest
    fun tearDown() {
        runCatching { tempFile.delete() }
    }

    @Test
    fun roundTripEnqueueRemove() {
        val store = DarkfiChatOutgoingQueueStore(tempFile)
        val r =
            PendingOutgoingChatRecord(
                id = "id-1",
                channelNormalized = "#dev",
                textSanitized = "hi",
                queuedAtEpochMs = 42L,
            )
        store.enqueue(r)
        assertEquals(1, store.loadAll().size)
        assertEquals("hi", store.loadAll().single().textSanitized)
        store.remove("id-1")
        assertTrue(store.loadAll().isEmpty())
    }

    @Test
    fun corruptFileYieldsEmptySnapshot() {
        tempFile.writeText("{ not valid json")
        val store = DarkfiChatOutgoingQueueStore(tempFile)
        assertTrue(store.loadAll().isEmpty())
    }

    @Test
    fun sanitizeBodyRejectsBlankForQueue() {
        assertTrue(DarkircPrivmsgRules.sanitizeBody("   \n\r  ") == null)
    }
}
