package com.nighthawkapps.lib.android.ui.screen.wallet.model

import com.nighthawkapps.lib.android.sdk.wallet.DarkfiTokenBalance

sealed interface BalanceViewType {
    data object SWIPE : BalanceViewType

    data object TOTAL : BalanceViewType

    data class Token(
        val balance: DarkfiTokenBalance,
    ) : BalanceViewType

    companion object {
        /** Native DRK lives on [TOTAL]; extra tokens are later pager pages. */
        fun pages(tokenBalances: List<DarkfiTokenBalance>): List<BalanceViewType> {
            val extra = tokenBalances.filterNot { it.isNativeDrk() }
            return buildList {
                add(SWIPE)
                add(TOTAL)
                extra.forEach { add(Token(it)) }
            }
        }
    }
}

internal fun DarkfiTokenBalance.isNativeDrk(): Boolean =
    displayName.equals("DRK", ignoreCase = true) ||
        tokenId.equals("DRK", ignoreCase = true)
