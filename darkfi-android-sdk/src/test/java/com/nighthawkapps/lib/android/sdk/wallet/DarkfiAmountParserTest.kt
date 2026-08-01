package com.nighthawkapps.lib.android.sdk.wallet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DarkfiAmountParserTest {
    @Test
    fun parses_decimal_amounts() {
        assertEquals("1.5", DarkfiAmountParser.parseDisplayAmountToDrkString("1.5"))
        assertEquals("0.00000001", DarkfiAmountParser.parseDisplayAmountToDrkString("0.00000001"))
    }

    @Test
    fun parses_atomic_units() {
        assertEquals(150_000_000L, DarkfiAmountParser.parseDisplayAmountToAtomic("1.5"))
        assertEquals(1L, DarkfiAmountParser.parseDisplayAmountToAtomic("0.00000001"))
    }

    @Test
    fun rejects_invalid_amounts() {
        assertNull(DarkfiAmountParser.parseDisplayAmountToDrkString(""))
        assertNull(DarkfiAmountParser.parseDisplayAmountToDrkString("abc"))
        assertNull(DarkfiAmountParser.parseDisplayAmountToDrkString("0"))
        assertNull(DarkfiAmountParser.parseDisplayAmountToDrkString("-1"))
    }
}
