package com.nighthawkapps.lib.android.sdk.wallet

/**
 * Outgoing payment memo limits for Nighthawk send.
 *
 * Upstream Money `MoneyNote.memo` is unbounded `Vec<u8>` (gas/tx-size limited).
 * Nighthawk does not put user text in that field; it is carried in UnifOMR
 * metadata whose length is a `u8`, so the user-visible memo is at most
 * [MAX_BYTES] UTF-8 bytes (matches `MAX_PAYMENT_MEMO_BYTES` in FFI `memo.rs`).
 */
object DarkfiPaymentMemo {
    const val MAX_BYTES: Int = 255

    fun utf8Size(input: String): Int = input.toByteArray(Charsets.UTF_8).size

    fun truncateToMaxBytes(
        input: String,
        maxBytes: Int = MAX_BYTES
    ): String {
        val bytes = input.toByteArray(Charsets.UTF_8)
        if (bytes.size <= maxBytes) return input
        var end = maxBytes.coerceIn(0, bytes.size)
        while (end > 0 && (bytes[end].toInt() and 0xC0) == 0x80) {
            end--
        }
        return String(bytes, 0, end, Charsets.UTF_8)
    }

    fun normalize(input: String?): String? {
        val trimmed = input?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return null
        }
        require(utf8Size(trimmed) <= MAX_BYTES) {
            "Memo exceeds $MAX_BYTES UTF-8 bytes"
        }
        return trimmed
    }
}
