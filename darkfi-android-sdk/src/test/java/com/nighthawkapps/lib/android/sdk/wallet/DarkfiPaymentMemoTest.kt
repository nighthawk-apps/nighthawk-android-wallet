package com.nighthawkapps.lib.android.sdk.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DarkfiPaymentMemoTest {
    @Test
    fun normalize_blank_returnsNull() {
        assertNull(DarkfiPaymentMemo.normalize(null))
        assertNull(DarkfiPaymentMemo.normalize("   "))
    }

    @Test
    fun normalize_trims() {
        assertEquals("hello", DarkfiPaymentMemo.normalize("  hello  "))
    }

    @Test
    fun normalize_accepts_255_ascii() {
        val memo = "x".repeat(DarkfiPaymentMemo.MAX_BYTES)
        assertEquals(memo, DarkfiPaymentMemo.normalize(memo))
    }

    @Test
    fun normalize_rejects_256_ascii() {
        val failed =
            runCatching { DarkfiPaymentMemo.normalize("x".repeat(DarkfiPaymentMemo.MAX_BYTES + 1)) }
                .isFailure
        assertTrue(failed)
    }

    @Test
    fun normalize_rejects_emoji_over_byte_budget() {
        // U+2615 HOT BEVERAGE is 3 UTF-8 bytes; 86 * 3 = 258 > 255
        val failed = runCatching { DarkfiPaymentMemo.normalize("☕".repeat(86)) }.isFailure
        assertTrue(failed)
    }

    @Test
    fun truncate_keeps_utf8_boundary() {
        val truncated = DarkfiPaymentMemo.truncateToMaxBytes("☕".repeat(100))
        assertTrue(DarkfiPaymentMemo.utf8Size(truncated) <= DarkfiPaymentMemo.MAX_BYTES)
        assertEquals(85, truncated.length)
        assertEquals("☕".repeat(85), truncated)
    }
}
