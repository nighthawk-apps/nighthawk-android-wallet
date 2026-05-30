@file:Suppress("ReturnCount")

package com.nighthawkapps.lib.android.sdk.wallet

import java.math.BigDecimal
import java.math.RoundingMode

/** Parses UI DRK amounts into the decimal strings consumed by upstream `drk.transfer`. */
object DarkfiAmountParser {
    const val DRK_DECIMALS = 8

    /**
     * Converts a user-entered amount (e.g. `1.5`) into a `drk`-compatible decimal string.
     * Returns null when the input is empty, non-numeric, or not positive.
     */
    fun parseDisplayAmountToDrkString(display: String): String? {
        val trimmed = display.trim()
        if (trimmed.isEmpty()) return null
        return runCatching {
            val bd = BigDecimal(trimmed)
            if (bd <= BigDecimal.ZERO) return null
            bd.setScale(DRK_DECIMALS, RoundingMode.DOWN).stripTrailingZeros().toPlainString()
        }.getOrNull()
    }

    /** Smallest-unit DRK for balance checks (matches upstream `decode_base10` rounding). */
    fun parseDisplayAmountToAtomic(display: String): Long? {
        val drkAmount = parseDisplayAmountToDrkString(display) ?: return null
        return runCatching {
            BigDecimal(drkAmount)
                .movePointRight(DRK_DECIMALS)
                .setScale(0, RoundingMode.DOWN)
                .longValueExact()
        }.getOrNull()
    }
}

/** Whether [confirmedBalanceAtomic] covers [amountAtomic] plus [feeAtomic]. */
fun hasSufficientBalanceForTransfer(
    confirmedBalanceAtomic: Long,
    amountAtomic: Long,
    feeAtomic: Long,
): Boolean {
    val required = amountAtomic + feeAtomic
    return required >= 0 && confirmedBalanceAtomic >= required
}
