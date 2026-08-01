package com.nighthawkapps.lib.android.sdk.wallet

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test that payment memo normalization runs on device (no live wallet required).
 * Full send-with-memo on testnet still needs a funded wallet and native `drk` — track as manual QA.
 */
@RunWith(AndroidJUnit4::class)
class DarkfiPaymentMemoInstrumentedTest {
    @Test
    fun normalize_accepts_short_memo() {
        val result = DarkfiPaymentMemo.normalize("hello")
        assertTrue(result == "hello")
    }

    @Test
    fun normalize_rejects_overlong_memo() {
        val longMemo = "x".repeat(DarkfiPaymentMemo.MAX_BYTES + 1)
        val failed =
            runCatching { DarkfiPaymentMemo.normalize(longMemo) }.isFailure
        assertTrue(failed)
    }
}
