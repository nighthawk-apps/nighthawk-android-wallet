package com.nighthawkapps.lib.android.sdk.chat.darkirc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DarkircDmPubkeyParserTest {
    @Test
    fun `extractFromText parses canonical prefix`() {
        val key = "11111111111111111111111111111111"
        val text = "${DarkircDmPubkeyParser.SHARE_PREFIX}$key"
        assertEquals(key, DarkircDmPubkeyParser.extractFromText(text))
    }

    @Test
    fun `extractFromText returns null for garbage`() {
        assertNull(DarkircDmPubkeyParser.extractFromText("hello #dev"))
        assertNull(DarkircDmPubkeyParser.extractFromText(""))
    }

    @Test
    fun `formatShareLine wraps pubkey`() {
        val key = "11111111111111111111111111111111"
        assertEquals(
            "${DarkircDmPubkeyParser.SHARE_PREFIX}$key",
            DarkircDmPubkeyParser.formatShareLine(key),
        )
    }

    @Test
    fun `parseCard parses nighthawk contact uri with dr flag and keeps legacy false`() {
        val key = "11111111111111111111111111111111"
        val cardUri = DarkircDmPubkeyParser.formatContactCard(key, nick = "alice", ratchetCapable = true)
        val parsed = DarkircDmPubkeyParser.parseCard(cardUri)
        assertEquals(key, parsed?.publicKeyBase58)
        assertEquals("alice", parsed?.nick)
        assertEquals(true, parsed?.ratchetCapable)

        val legacyParsed = DarkircDmPubkeyParser.parseCard("${DarkircDmPubkeyParser.SHARE_PREFIX}$key")
        assertEquals(key, legacyParsed?.publicKeyBase58)
        assertEquals(false, legacyParsed?.ratchetCapable)
    }
}
