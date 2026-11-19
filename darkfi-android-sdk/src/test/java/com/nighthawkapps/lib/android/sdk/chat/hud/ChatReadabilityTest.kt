package com.nighthawkapps.lib.android.sdk.chat.hud

import com.nighthawkapps.lib.android.sdk.chat.ChatChannelMessage
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatReadabilityTest {
    @Test
    fun timeline_insertsDateSeparatorWhenDayChanges() {
        val day1 = 1_746_662_400_000L // 2025-05-08 00:00 UTC
        val day2 = day1 + 86_400_000L
        val items =
            ChatTimeline.group(
                listOf(
                    msg("a", day1),
                    msg("b", day1 + 60_000),
                    msg("c", day2),
                ),
                ZoneOffset.UTC,
            )
        val separators = items.filterIsInstance<ChatTimelineItem.DateSeparator>()
        assertEquals(2, separators.size)
        assertEquals(3, items.filterIsInstance<ChatTimelineItem.Message>().size)
    }

    @Test
    fun gutterTime_isHourMinuteUtc() {
        assertEquals("00:01", ChatTimeline.gutterTime(60_000L, ZoneOffset.UTC))
    }

    @Test
    fun nickPalette_isStableCaseInsensitiveAndWithinStealthSet() {
        val first = ChatChrome.peerNickArgb("alice")
        assertEquals(first, ChatChrome.peerNickArgb("alice"))
        assertEquals(first, ChatChrome.peerNickArgb("ALICE"))
        assertTrue(first in ChatChrome.PEER_NICKS)
        assertEquals(ChatChrome.peerNickIndex("alice"), ChatChrome.peerNickIndex("ALICE"))
    }

    @Test
    fun ownNick_usesAccentNotPeerHash() {
        assertTrue(ChatChrome.isOwnNick("HawkOne", "hawkone"))
        assertEquals(ChatChrome.OWN_NICK, ChatChrome.nickArgb("HawkOne", "hawkone"))
        assertEquals(ChatChrome.TIMESTAMP, ChatChrome.nickArgb("System", "hawkone"))
        assertEquals(ChatChrome.BODY_OUTGOING, ChatChrome.bodyArgb(true))
        assertEquals(ChatChrome.BODY_INCOMING, ChatChrome.bodyArgb(false))
    }

    @Test
    fun threadIsEncrypted_directOnlyNotPublicChannels() {
        assertTrue(ChatChrome.threadIsEncrypted(true, "alice", emptyList()))
        assertFalse(ChatChrome.threadIsEncrypted(false, "#dev", listOf("#Dev")))
        assertFalse(ChatChrome.threadIsEncrypted(false, "#dev", emptyList()))
        assertFalse(ChatChrome.threadIsEncrypted(true, "", listOf("#dev")))
        assertFalse(ChatChrome.threadIsEncrypted(true, "#dev", listOf("#dev")))
    }

    @Test
    fun timeline_outOfOrderDagRepeatsSameEpochDay() {
        val day1 = 1_746_662_400_000L
        val day2 = day1 + 86_400_000L
        val items =
            ChatTimeline.group(
                listOf(
                    msg("a", day1),
                    msg("b", day2),
                    msg("c", day1 + 1_000),
                ),
                ZoneOffset.UTC,
            )
        val days = items.filterIsInstance<ChatTimelineItem.DateSeparator>().map { it.epochDay }
        assertEquals(listOf(days[0], days[1], days[0]), days)
    }

    @Test
    fun lexer_splitsUrlsAndFudFromPlainText() {
        val spans =
            ChatMessageLexer.lex(
                "see https://dark.fi/docs and fud://QmHash/file.png thanks.",
            )
        assertEquals(ChatInlineSpan.Text("see "), spans[0])
        assertEquals(ChatInlineSpan.Url("https://dark.fi/docs"), spans[1])
        assertTrue(spans[2] is ChatInlineSpan.Text)
        assertEquals(ChatInlineSpan.Fud("fud://QmHash/file.png"), spans[3])
        assertTrue(ChatMessageLexer.hasFud("fud://abc"))
        assertEquals(listOf("fud://QmHash/file.png"), ChatMessageLexer.fudUris("see fud://QmHash/file.png"))
    }

    private fun msg(
        id: String,
        ts: Long,
    ) = ChatChannelMessage(
        eventId = id,
        channel = "#dev",
        nick = "alice",
        text = "hi",
        timestampMs = ts,
    )
}
