package com.nighthawkapps.lib.android.ui.screen.wallet.model

import com.nighthawkapps.lib.android.sdk.wallet.DarkfiTokenBalance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceViewTypeTest {
    @Test
    fun pages_areSwipeThenAssets() {
        assertEquals(listOf(BalanceViewType.SWIPE, BalanceViewType.ASSETS), BalanceViewType.pages())
    }

    @Test
    fun portfolio_putsDrkFirstThenSortedExtras() {
        val rows =
            portfolioRows(
                nativeAtomic = 42L,
                tokenBalances =
                    listOf(
                        DarkfiTokenBalance(tokenId = "zzz", displayLabel = "Zed", balanceAtomic = 1L),
                        DarkfiTokenBalance(tokenId = "DRK", displayLabel = "DRK", balanceAtomic = 99L),
                        DarkfiTokenBalance(tokenId = "aaa", displayLabel = "Alpha", balanceAtomic = 2L),
                    ),
            )
        assertEquals("DRK", rows.first().displayName)
        assertEquals(42L, rows.first().balanceAtomic)
        assertEquals(listOf("DRK", "Alpha", "Zed"), rows.map { it.displayName })
    }

    @Test
    fun portfolio_synthesizesDrkWhenListEmpty() {
        val rows = portfolioRows(nativeAtomic = 0L, tokenBalances = emptyList())
        assertEquals(1, rows.size)
        assertTrue(rows.first().isNativeDrk())
    }
}
