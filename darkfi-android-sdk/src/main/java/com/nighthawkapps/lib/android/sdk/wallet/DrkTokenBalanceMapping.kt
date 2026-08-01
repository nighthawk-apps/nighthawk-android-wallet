package com.nighthawkapps.lib.android.sdk.wallet

import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.DrkTokenBalance

internal object DrkTokenBalanceMapping {
    fun balance(record: DrkTokenBalance): DarkfiTokenBalance =
        DarkfiTokenBalance(
            tokenId = record.tokenId,
            displayLabel = record.displayLabel,
            balanceAtomic = record.balanceAtomic,
        )

    fun balances(records: List<DrkTokenBalance>): List<DarkfiTokenBalance> = records.map(::balance)
}
