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

    @Test
    fun max_spendable_subtracts_fee_from_same_asset() {
        assertEquals(90L, maxSpendableAtomic(availableAtomic = 100L, feeAtomic = 10L, feePaidFromThisAsset = true))
    }

    @Test
    fun max_spendable_keeps_full_balance_for_other_assets() {
        assertEquals(100L, maxSpendableAtomic(availableAtomic = 100L, feeAtomic = 10L, feePaidFromThisAsset = false))
    }

    @Test
    fun max_spendable_without_fee_is_full_balance() {
        assertEquals(100L, maxSpendableAtomic(availableAtomic = 100L, feeAtomic = null))
    }

    @Test
    fun resolve_selected_asset_uses_token_balance_for_custom_token() {
        val tokens =
            listOf(
                DarkfiTokenBalance(tokenId = "DRK", displayLabel = "DRK", balanceAtomic = 900L),
                DarkfiTokenBalance(tokenId = "tok_abc", displayLabel = "GOLD", balanceAtomic = 250_000_000L),
            )
        val selected =
            resolveSelectedAsset(
                confirmedDrkAtomic = 10_000_000L,
                tokenBalances = tokens,
                selectedTokenId = "tok_abc",
            )
        assertEquals("GOLD", selected.unit)
        assertEquals(250_000_000L, selected.availableAtomic)
        assertTrue(!selected.isNativeDrk)
    }

    @Test
    fun custom_token_checks_token_balance_and_drk_fee_separately() {
        // Enough token (2.0), enough DRK for fee (0.05) -> Ok
        assertEquals(
            SendBalanceCheck.Ok,
            evaluateAssetAndFeeBalance(
                assetAvailableAtomic = 200_000_000L,
                drkAvailableAtomic = 5_000_000L,
                amountDisplay = "2",
                feeAtomic = 5_000_000L,
                isNativeDrk = false,
                assetUnit = "GOLD",
            ),
        )

        // Enough token (2.0), insufficient DRK for fee -> Insufficient in DRK
        val missingFee =
            evaluateAssetAndFeeBalance(
                assetAvailableAtomic = 200_000_000L,
                drkAvailableAtomic = 1_000_000L,
                amountDisplay = "2",
                feeAtomic = 5_000_000L,
                isNativeDrk = false,
                assetUnit = "GOLD",
            ) as SendBalanceCheck.Insufficient
        assertEquals("DRK", missingFee.unit)
        assertEquals(1_000_000L, missingFee.availableAtomic)
        assertEquals(5_000_000L, missingFee.requiredAtomic)

        // Insufficient token (1.0 < 2.0), plenty of DRK -> Insufficient in token unit
        val missingToken =
            evaluateAssetAndFeeBalance(
                assetAvailableAtomic = 100_000_000L,
                drkAvailableAtomic = 500_000_000L,
                amountDisplay = "2",
                feeAtomic = 5_000_000L,
                isNativeDrk = false,
                assetUnit = "GOLD",
            ) as SendBalanceCheck.Insufficient
        assertEquals("GOLD", missingToken.unit)
        assertEquals(100_000_000L, missingToken.availableAtomic)
        assertEquals(200_000_000L, missingToken.requiredAtomic)
    }

    @Test
    fun unknown_token_id_is_not_treated_as_native_drk() {
        val tokens =
            listOf(
                DarkfiTokenBalance(tokenId = "DRK", displayLabel = "DRK", balanceAtomic = 9_000_000L),
            )
        assertTrue(!isNativeDrkToken("missing-token", tokens))
        val selected =
            resolveSelectedAsset(
                confirmedDrkAtomic = 50_000_000L,
                tokenBalances = tokens,
                selectedTokenId = "missing-token",
            )
        assertEquals("missing-token", selected.unit)
        assertEquals(0L, selected.availableAtomic)
        assertTrue(!selected.isNativeDrk)
        val check =
            evaluateAssetAndFeeBalance(
                assetAvailableAtomic = selected.availableAtomic,
                drkAvailableAtomic = 50_000_000L,
                amountDisplay = "1",
                feeAtomic = 5_000_000L,
                isNativeDrk = selected.isNativeDrk,
                assetUnit = selected.unit,
            ) as SendBalanceCheck.Insufficient
        assertEquals("missing-token", check.unit)
        assertEquals(0L, check.availableAtomic)
    }
}
