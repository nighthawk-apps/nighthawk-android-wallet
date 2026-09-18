package com.nighthawkapps.lib.android.ui.screen.wallet.model

import com.nighthawkapps.lib.android.sdk.wallet.DarkfiTokenBalance

sealed interface BalanceViewType {
    data object SWIPE : BalanceViewType

    /** Full asset portfolio (DRK first). */
    data object ASSETS : BalanceViewType

    companion object {
        fun pages(): List<BalanceViewType> = listOf(SWIPE, ASSETS)
    }
}

internal fun DarkfiTokenBalance.isNativeDrk(): Boolean =
    displayName.equals("DRK", ignoreCase = true) ||
        tokenId.equals("DRK", ignoreCase = true)

/** DRK always first, then every other token. Native balance comes from the snapshot. */
internal fun portfolioRows(
    nativeAtomic: Long,
    tokenBalances: List<DarkfiTokenBalance>,
): List<DarkfiTokenBalance> {
    val extras =
        tokenBalances
            .filterNot { it.isNativeDrk() }
            .sortedBy { it.displayName.lowercase() }
    val drk =
        DarkfiTokenBalance(
            tokenId = "DRK",
            displayLabel = "DRK",
            balanceAtomic = nativeAtomic,
        )
    return listOf(drk) + extras
}
