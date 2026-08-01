package com.nighthawkapps.lib.android.sdk.wallet

import kotlin.test.Test
import kotlin.test.assertTrue

class DarkfiTransferSupportTest {
    @Test
    fun parseAmount_rejects_zero() {
        val result = DarkfiTransferSupport.parseAmountDisplay("0")
        assertTrue(result is DarkfiTransferResult.Failure)
    }

    @Test
    fun validateRecipient_rejects_blank() {
        val result = DarkfiTransferSupport.validateRecipient("  ")
        assertTrue(result is DarkfiTransferResult.Failure)
    }
}
