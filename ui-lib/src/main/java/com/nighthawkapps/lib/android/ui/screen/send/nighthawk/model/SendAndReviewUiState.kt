package com.nighthawkapps.lib.android.ui.screen.send.nighthawk.model

data class SendAndReviewUiState(
    val amountToSend: String = "0.0",
    val amountUnit: String = "DRK",
    val convertedAmountWithCurrency: String = "55 EUR",
    val memo: String = "",
    val network: String = "MainNet",
    val recipientType: String = "DarkFi",
    val receiverAddress: String = "",
    val subTotal: String = "",
    val networkFees: String = "",
    val totalAmount: String = ""
)
