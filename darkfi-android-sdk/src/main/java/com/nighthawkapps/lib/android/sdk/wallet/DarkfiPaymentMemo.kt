package com.nighthawkapps.lib.android.sdk.wallet

/** UTF-8 payment memo carried in encrypted `MoneyNote::memo` (upstream Money contract). */
object DarkfiPaymentMemo {
    const val MAX_BYTES: Int = 512

    fun normalize(input: String?): String? {
        val trimmed = input?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return null
        }
        require(trimmed.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) {
            "Memo exceeds $MAX_BYTES UTF-8 bytes"
        }
        return trimmed
    }
}
