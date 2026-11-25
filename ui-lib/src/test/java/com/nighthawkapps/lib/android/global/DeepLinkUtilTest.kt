package com.nighthawkapps.lib.android.global

import android.net.Uri
import android.util.Base64
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiPaymentMemo
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

    @Test
    fun acceptsMaxUtf8Memo() {
        val memo = "x".repeat(DarkfiPaymentMemo.MAX_BYTES)
        val b64 = Base64.encodeToString(memo.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val data = DeepLinkUtil.getSendDeepLinkData(Uri.parse("drk:drk_u1abc?memo=$b64"))
        assertEquals(memo, data!!.memo)
    }

    @Test
    fun roundTripsPaymentRequestUri() {
        val uri = DeepLinkUtil.buildPaymentRequestUri("drk_u1abc", "0.001", "aaa")
        assertNotNull(uri)
        val data = DeepLinkUtil.getSendDeepLinkData(Uri.parse(uri))
        assertEquals("drk_u1abc", data!!.address)
        assertNotNull(data.amount)
        assertEquals("aaa", data.memo)
    }

    @Test
    fun rejectsOversizedMemo() {
        val memo = "x".repeat(DarkfiPaymentMemo.MAX_BYTES + 1)
        val b64 = Base64.encodeToString(memo.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val data = DeepLinkUtil.getSendDeepLinkData(Uri.parse("drk:drk_u1abc?memo=$b64"))
        assertNotNull(data)
        assertEquals("drk_u1abc", data!!.address)
        assertNull(data.memo)
    }

    @Test
    fun roundTripsBase64MemoWithPlusSlashEqualsAndTrimmedAmount() {
        // Base64("ab>cd?e") == "YWI+Y2Q/ZQ==" — exercises '+', '/', and '=' in query params.
        val memo = "ab>cd?e"
        val expectedB64 = Base64.encodeToString(memo.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        assertEquals("YWI+Y2Q/ZQ==", expectedB64)

        val uri = DeepLinkUtil.buildPaymentRequestUri("drk_u1abc", "  0.001  ", memo)
        assertNotNull(uri)
        assertEquals(true, uri!!.contains("amount=0.001"))
        assertEquals(false, uri.contains("amount=%20"))
        assertEquals(true, uri.contains("%2B"))
        assertEquals(true, uri.contains("%2F"))
        assertEquals(true, uri.contains("%3D"))

        val data = DeepLinkUtil.getSendDeepLinkData(Uri.parse(uri))
        assertNotNull(data)
        assertEquals("drk_u1abc", data!!.address)
        assertEquals(100_000L, data.amount)
        assertEquals(memo, data.memo)
    }
}
