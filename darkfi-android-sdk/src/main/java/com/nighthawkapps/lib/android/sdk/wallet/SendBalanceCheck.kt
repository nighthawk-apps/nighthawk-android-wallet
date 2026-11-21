@file:Suppress("MaxLineLength", "ReturnCount", "LongParameterList")

package com.nighthawkapps.lib.android.sdk.wallet

/** Default DarkFi transfer fee in atomic units (`SCHEDULE_TRANSFER_FEE_ATOMIC`). */
const val DEFAULT_TRANSFER_FEE_ATOMIC: Long = 5_000_000L

/** Result of comparing wallet balance to amount + estimated fee before broadcast. */
sealed interface SendBalanceCheck {
    data object Ok : SendBalanceCheck

    /** Fee estimate has not completed yet. */
    data object FeePending : SendBalanceCheck

    data class Insufficient(
        val availableAtomic: Long,
        val requiredAtomic: Long,
        val unit: String = "DRK",
    ) : SendBalanceCheck
}

data class SelectedSendAsset(
    val unit: String,
    val availableAtomic: Long,
    val isNativeDrk: Boolean,
)

fun isNativeDrkToken(
    selectedTokenId: String?,
    tokenBalances: List<DarkfiTokenBalance>,
): Boolean {
    val id = selectedTokenId?.takeIf { it.isNotBlank() } ?: return true
    if (id.equals("DRK", ignoreCase = true)) return true
    // A token id the wallet does not hold is not DRK. Treating it as native
    // would let a custom-token send spend the DRK balance.
    val matched = tokenBalances.firstOrNull { it.tokenId == id } ?: return false
    return matched.displayName.equals("DRK", ignoreCase = true) ||
        matched.tokenId.equals("DRK", ignoreCase = true)
}

fun resolveSelectedAsset(
    confirmedDrkAtomic: Long,
    tokenBalances: List<DarkfiTokenBalance>,
    selectedTokenId: String?,
): SelectedSendAsset {
    val id = selectedTokenId?.takeIf { it.isNotBlank() }
    val matched = id?.let { tokenBalances.firstOrNull { it.tokenId == id } }
    val isNative = isNativeDrkToken(selectedTokenId, tokenBalances)
    if (isNative) {
        return SelectedSendAsset(
            unit = "DRK",
            availableAtomic = confirmedDrkAtomic,
            isNativeDrk = true,
        )
    }
    if (matched == null) {
        return SelectedSendAsset(
            unit = id ?: "token",
            availableAtomic = 0L,
            isNativeDrk = false,
        )
    }
    return SelectedSendAsset(
        unit = matched.displayName,
        availableAtomic = matched.balanceAtomic,
        isNativeDrk = false,
    )
}

/**
 * Largest amount that still leaves room for [feeAtomic] when the fee is paid
 * from the same asset. Unknown fees reserve nothing; callers should re-clamp
 * after [evaluateSendBalance] once a fee estimate arrives.
 */
fun maxSpendableAtomic(
    availableAtomic: Long,
    feeAtomic: Long?,
    feePaidFromThisAsset: Boolean = true,
): Long {
    val reserve = if (feePaidFromThisAsset) feeAtomic?.coerceAtLeast(0L) ?: 0L else 0L
    return (availableAtomic - reserve).coerceAtLeast(0L)
}

fun evaluateSendBalance(
    confirmedBalanceAtomic: Long,
    amountDisplay: String,
    feeAtomic: Long?,
): SendBalanceCheck =
    evaluateAssetAndFeeBalance(
        assetAvailableAtomic = confirmedBalanceAtomic,
        drkAvailableAtomic = confirmedBalanceAtomic,
        amountDisplay = amountDisplay,
        feeAtomic = feeAtomic,
        isNativeDrk = true,
        assetUnit = "DRK",
    )

fun evaluateAssetAndFeeBalance(
    assetAvailableAtomic: Long,
    drkAvailableAtomic: Long,
    amountDisplay: String,
    feeAtomic: Long?,
    isNativeDrk: Boolean,
    assetUnit: String = "DRK",
): SendBalanceCheck {
    val amountAtomic = DarkfiAmountParser.parseDisplayAmountToAtomic(amountDisplay) ?: return SendBalanceCheck.FeePending
    val fee = feeAtomic ?: return SendBalanceCheck.FeePending
    if (isNativeDrk) {
        val required = amountAtomic + fee
        return if (hasSufficientBalanceForTransfer(drkAvailableAtomic, amountAtomic, fee)) {
            SendBalanceCheck.Ok
        } else {
            SendBalanceCheck.Insufficient(
                availableAtomic = drkAvailableAtomic,
                requiredAtomic = required,
                unit = "DRK",
            )
        }
    }
    if (assetAvailableAtomic < amountAtomic) {
        return SendBalanceCheck.Insufficient(
            availableAtomic = assetAvailableAtomic,
            requiredAtomic = amountAtomic,
            unit = assetUnit,
        )
    }
    if (drkAvailableAtomic < fee) {
        return SendBalanceCheck.Insufficient(
            availableAtomic = drkAvailableAtomic,
            requiredAtomic = fee,
            unit = "DRK",
        )
    }
    return SendBalanceCheck.Ok
}
