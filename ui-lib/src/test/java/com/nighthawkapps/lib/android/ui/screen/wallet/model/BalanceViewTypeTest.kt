package com.nighthawkapps.lib.android.ui.screen.wallet.model

import com.nighthawkapps.lib.android.sdk.wallet.DarkfiTokenBalance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceViewTypeTest {
    @Test
    fun pages_withoutTokens_isSwipeThenTotal() {
        val pages = BalanceViewType.pages(emptyList())
        assertEquals(listOf(BalanceViewType.SWIPE, BalanceViewType.TOTAL), pages)
    }

    @Test
    fun pages_nativeDrkOnly_doesNotAddExtraPage() {
        val pages =
            BalanceViewType.pages(
                listOf(DarkfiTokenBalance(tokenId = "DRK", displayLabel = "DRK", balanceAtomic = 0L)),
            )
        assertEquals(2, pages.size)
        assertEquals(BalanceViewType.SWIPE, pages[0])
        assertEquals(BalanceViewType.TOTAL, pages[1])
    }

    @Test
    fun pages_appendsExtraTokensAfterTotal() {
        val extra = DarkfiTokenBalance(tokenId = "token-abc", displayLabel = "DAO", balanceAtomic = 5L)
        val pages =
            BalanceViewType.pages(
                listOf(
                    DarkfiTokenBalance(tokenId = "DRK", displayLabel = "DRK", balanceAtomic = 1L),
                    extra,
                ),
            )
        assertEquals(3, pages.size)
        assertEquals(BalanceViewType.Token(extra), pages[2])
    }

    @Test
    fun isNativeDrk_matchesLabelOrId() {
        assertTrue(
            DarkfiTokenBalance(tokenId = "DRK", displayLabel = null, balanceAtomic = 0L).isNativeDrk(),
        )
        assertTrue(
            DarkfiTokenBalance(tokenId = "0xabc", displayLabel = "drk", balanceAtomic = 0L).isNativeDrk(),
        )
    }
}
