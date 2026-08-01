package com.nighthawkapps.lib.android.sdk.wallet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SendBalanceCheckTest {
    @Test
    fun sufficient_when_balance_covers_amount_and_fee() {
        val check =
            evaluateSendBalance(
                confirmedBalanceAtomic = 100_000_100L,
                amountDisplay = "1",
                feeAtomic = 50L,
            )
        assertEquals(SendBalanceCheck.Ok, check)
    }

    @Test
    fun insufficient_when_balance_too_low() {
        val check =
            evaluateSendBalance(
                confirmedBalanceAtomic = 100L,
                amountDisplay = "1",
                feeAtomic = 50L
            ) as SendBalanceCheck.Insufficient
        assertEquals(100L, check.availableAtomic)
        assertEquals(100_000_000L + 50L, check.requiredAtomic)
    }

    @Test
    fun fee_pending_when_fee_unknown() {
        val check = evaluateSendBalance(confirmedBalanceAtomic = 1_000_000L, amountDisplay = "1", feeAtomic = null)
        assertEquals(SendBalanceCheck.FeePending, check)
    }
}
