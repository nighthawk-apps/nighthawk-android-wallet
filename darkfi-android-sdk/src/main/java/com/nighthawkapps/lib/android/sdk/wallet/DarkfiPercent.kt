package com.nighthawkapps.lib.android.sdk.wallet

data class DarkfiPercent(
    val decimal: Float
) {
    fun isLessThanHundredPercent(): Boolean = decimal < 1f - 1e-4f

    companion object {
        val ZERO_PERCENT = DarkfiPercent(0f)
        val HUNDRED_PERCENT = DarkfiPercent(1f)
    }
}
