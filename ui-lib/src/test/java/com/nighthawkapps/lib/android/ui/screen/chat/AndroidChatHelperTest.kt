package com.nighthawkapps.lib.android.ui.screen.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidChatHelperTest {
    @Test
    fun shouldAutoScroll_trueWhenEmptyOrNearBottom() {
        assertTrue(shouldAutoScrollOnNewMessage(lastVisibleIndex = 0, totalItemsCount = 0))
        assertTrue(shouldAutoScrollOnNewMessage(lastVisibleIndex = 9, totalItemsCount = 10))
        assertTrue(shouldAutoScrollOnNewMessage(lastVisibleIndex = 8, totalItemsCount = 10))
        assertTrue(shouldAutoScrollOnNewMessage(lastVisibleIndex = 7, totalItemsCount = 10))
    }

    @Test
    fun shouldAutoScroll_falseWhenScrolledUpReadingHistory() {
        assertFalse(shouldAutoScrollOnNewMessage(lastVisibleIndex = 2, totalItemsCount = 50))
        assertFalse(shouldAutoScrollOnNewMessage(lastVisibleIndex = 40, totalItemsCount = 50))
    }

    @Test
    fun formatTlsPinSummary_formatsPrefixOrReturnsNullWhenEmpty() {
        assertNull(formatTlsPinSummary(null))
        assertNull(formatTlsPinSummary(emptyList()))
        val bytes = List(32) { it.toUByte() }
        assertEquals("sha256:0001020304050607...", formatTlsPinSummary(bytes))
    }
}
