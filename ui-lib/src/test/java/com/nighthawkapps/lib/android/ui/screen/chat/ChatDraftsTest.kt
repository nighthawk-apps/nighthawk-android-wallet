package com.nighthawkapps.lib.android.ui.screen.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatDraftsTest {
    @Test
    fun switchStashesAndRestoresEachRoom() {
        val drafts = mutableMapOf<String, String>()
        val channel = ChatDrafts.key(direct = false, channel = "#dev", dm = null)
        val other = ChatDrafts.key(direct = false, channel = "#market", dm = null)
        val dm = ChatDrafts.key(direct = true, channel = "#dev", dm = "alice")

        val after = ChatDrafts.switch(drafts, channel, other, "keep-dev")
        assertEquals("", after)
        assertEquals("keep-dev", drafts[channel])

        val back = ChatDrafts.switch(drafts, other, channel, "keep-market")
        assertEquals("keep-dev", back)
        assertEquals("keep-market", drafts[other])

        val intoDm = ChatDrafts.switch(drafts, channel, dm, "edited")
        assertEquals("", intoDm)
        assertEquals("edited", drafts[channel])

        ChatDrafts.clear(drafts, dm)
        assertEquals("", ChatDrafts.switch(drafts, other, dm, ""))
        assertEquals(null, drafts[dm])
    }

    @Test
    fun sameRoomKeepsTheCurrentText() {
        val drafts = mutableMapOf<String, String>()
        val channel = ChatDrafts.key(direct = false, channel = "#dev", dm = null)
        assertEquals("still", ChatDrafts.switch(drafts, channel, channel, "still"))
        assertEquals(0, drafts.size)
    }
}
