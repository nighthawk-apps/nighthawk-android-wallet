package com.nighthawkapps.lib.android.sdk.wallet

internal object DarkfiTransferSupport {
    fun parseAmountDisplay(amountDisplay: String): DarkfiTransferResult<String> {
        val drkAmount =
            DarkfiAmountParser.parseDisplayAmountToDrkString(amountDisplay)
                ?: return DarkfiTransferResult.Failure("Enter a valid DRK amount")
        return DarkfiTransferResult.Success(drkAmount)
    }

    fun validateRecipient(recipientAddress: String): DarkfiTransferResult<String> {
        val trimmed = recipientAddress.trim()
        if (trimmed.isBlank()) {
            return DarkfiTransferResult.Failure("Recipient address is required")
        }
        return DarkfiTransferResult.Success(trimmed)
    }
}
