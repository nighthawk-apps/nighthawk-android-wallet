package com.nighthawkapps.lib.android.sdk.chat.darkirc

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DarkircDmPubkeyParserInstrumentedTest {
    @Test
    fun extractFromText_parsesCanonicalPrefix() {
        val key = "11111111111111111111111111111111"
        val text = "${DarkircDmPubkeyParser.SHARE_PREFIX}$key"
        assertEquals(key, DarkircDmPubkeyParser.extractFromText(text))
    }

    @Test
    fun extractFromText_rejectsGarbage() {
        assertNull(DarkircDmPubkeyParser.extractFromText("hello #dev"))
    }
}
