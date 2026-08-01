package com.nighthawkapps.lib.android.sdk.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
