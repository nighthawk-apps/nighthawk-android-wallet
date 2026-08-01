package com.nighthawkapps.lib.android.ui.screen.wallet.model

sealed interface BalanceViewType {
    object SWIPE : BalanceViewType

    object TOTAL : BalanceViewType

    companion object {
        const val TOTAL_VIEWS = 2

        fun getBalanceViewType(pos: Int): BalanceViewType =
            when (pos) {
                1 -> TOTAL
                else -> SWIPE
            }
    }
}
