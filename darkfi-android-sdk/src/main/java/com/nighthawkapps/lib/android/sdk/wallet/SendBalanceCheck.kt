@file:Suppress("MaxLineLength", "ReturnCount")

package com.nighthawkapps.lib.android.sdk.wallet

/** Result of comparing wallet balance to amount + estimated fee before broadcast. */
sealed interface SendBalanceCheck {
    data object Ok : SendBalanceCheck

    /** Fee estimate has not completed yet. */
    data object FeePending : SendBalanceCheck

    data class Insufficient(
        val availableAtomic: Long,
        val requiredAtomic: Long,
    ) : SendBalanceCheck
}

fun evaluateSendBalance(
    confirmedBalanceAtomic: Long,
    amountDisplay: String,
    feeAtomic: Long?,
): SendBalanceCheck {
    val amountAtomic = DarkfiAmountParser.parseDisplayAmountToAtomic(amountDisplay) ?: return SendBalanceCheck.FeePending
    val fee = feeAtomic ?: return SendBalanceCheck.FeePending
    val required = amountAtomic + fee
    return if (hasSufficientBalanceForTransfer(confirmedBalanceAtomic, amountAtomic, fee)) {
        SendBalanceCheck.Ok
    } else {
        SendBalanceCheck.Insufficient(
            availableAtomic = confirmedBalanceAtomic,
            requiredAtomic = required,
        )
    }
}
