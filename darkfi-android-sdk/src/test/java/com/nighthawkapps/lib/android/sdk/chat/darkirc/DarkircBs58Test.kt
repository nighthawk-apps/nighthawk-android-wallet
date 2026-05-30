package com.nighthawkapps.lib.android.sdk.chat.darkirc

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DarkircBs58Test {
    @Test
    fun decode32_allZeroBytes() {
        val decoded = DarkircBs58.decode32OrNull("11111111111111111111111111111111")
        assertNotNull(decoded)
        assertTrue(decoded.size == 32)
        assertTrue(decoded.all { it == 0.toByte() })
    }

    @Test
    fun rejectsWrongLength() {
        assertNull(DarkircBs58.decode32OrNull("1111"))
        assertFalse(DarkircBs58.isValidSecret32("not-base58!"))
    }
}
