package com.nighthawkapps.lib.android.sdk.wallet

import java.math.BigDecimal
import java.math.RoundingMode

object DarkfiAmountFormatter {
    private const val DISPLAY_SCALE = 8

    /** Formats smallest-unit DRK (e.g. 10^8 per coin) for UI. */
    fun formatAtomic(
        atomic: Long,
        decimals: Int = 8
    ): String {
        val bd = BigDecimal(atomic).movePointLeft(decimals)
        return bd.setScale(DISPLAY_SCALE, RoundingMode.DOWN).stripTrailingZeros().toPlainString()
    }
}
