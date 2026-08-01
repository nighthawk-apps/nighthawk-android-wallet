package com.nighthawkapps.lib.android.global

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeepLinkUtilTest {
    @Test
    fun acceptsAddressOnlyDrkLink() {
        val data = DeepLinkUtil.getSendDeepLinkData(Uri.parse("drk:drk_u1abc"))
        assertNotNull(data)
        assertEquals("drk_u1abc", data!!.address)
        assertNull(data.amount)
        assertNull(data.memo)
    }

    @Test
    fun acceptsAmountAndMemoQueryParams() {
        val data = DeepLinkUtil.getSendDeepLinkData(Uri.parse("drk:drk_u1abc?amount=0.001&memo=YWFh"))
        assertNotNull(data)
        assertEquals("drk_u1abc", data!!.address)
        assertNotNull(data.amount)
        assertEquals("aaa", data.memo)
    }

    @Test
    fun rejectsNonDrkScheme() {
        assertNull(DeepLinkUtil.getSendDeepLinkData(Uri.parse("https://example.com?amount=1")))
    }

    @Test
    fun rejectsInvalidAmount() {
        assertNull(DeepLinkUtil.getSendDeepLinkData(Uri.parse("drk:drk_u1abc?amount=-1")))
    }
}
