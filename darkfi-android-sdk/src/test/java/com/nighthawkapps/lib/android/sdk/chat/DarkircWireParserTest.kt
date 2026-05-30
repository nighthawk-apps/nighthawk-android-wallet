package com.nighthawkapps.lib.android.sdk.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DarkircWireParserTest {
    @Test
    fun parsePrivmsg_basic() {
        val msg =
            DarkircWireParser.parsePrivmsg(":alice!~anon@darkirc PRIVMSG #dev :hello world")
        assertNotNull(msg)
        assertEquals("alice", msg.nick)
        assertEquals("#dev", msg.channel)
        assertEquals("hello world", msg.text)
    }

    @Test
    fun parsePrivmsg_invalid() {
        assertNull(DarkircWireParser.parsePrivmsg(":server 001 alice :Welcome"))
    }

    @Test
    fun parsePrivmsg_truncatedOrMalformed() {
        assertNull(DarkircWireParser.parsePrivmsg(":alice!~x PRIVMSG"))
        assertNull(DarkircWireParser.parsePrivmsg("PRIVMSG #dev :missing prefix nick"))
        assertNull(DarkircWireParser.parsePrivmsg(""))
    }

    @Test
    fun parsePrivmsg_allowsEmptyTrailingText() {
        val msg =
            DarkircWireParser.parsePrivmsg(":bob!~anon@darkirc PRIVMSG #random :")
        assertNotNull(msg)
        assertEquals("", msg.text)
    }

    @Test
    fun welcome_numeric() {
        assertTrue(DarkircWireParser.containsWelcomeNumeric(":darkirc 001 alice :Welcome"))
        assertFalse(DarkircWireParser.containsWelcomeNumeric(":darkirc 451 alice :You have not registered"))
    }

    @Test
    fun ping_detection() {
        assertTrue(DarkircWireParser.isPing("PING :darkirc"))
        assertFalse(DarkircWireParser.isPing("PONG :darkirc"))
        assertEquals("darkirc", DarkircWireParser.pongPayload("PING :darkirc"))
    }
}
