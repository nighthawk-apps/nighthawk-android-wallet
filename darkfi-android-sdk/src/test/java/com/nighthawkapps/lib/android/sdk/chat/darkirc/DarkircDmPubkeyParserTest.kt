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
}
