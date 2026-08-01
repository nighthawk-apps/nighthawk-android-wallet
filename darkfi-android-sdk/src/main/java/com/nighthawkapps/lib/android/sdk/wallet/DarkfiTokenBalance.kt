package com.nighthawkapps.lib.android.sdk.wallet

data class DarkfiTokenBalance(
    val tokenId: String,
    val displayLabel: String?,
    val balanceAtomic: Long,
) {
    val displayName: String
        get() = displayLabel?.takeIf { it.isNotBlank() } ?: tokenId
}
