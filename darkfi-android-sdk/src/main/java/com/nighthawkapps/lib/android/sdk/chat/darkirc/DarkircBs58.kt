@file:Suppress("MagicNumber")

package com.nighthawkapps.lib.android.sdk.chat.darkirc

/**
 * Validates DarkFi / NaCl-style **32-byte** secrets encoded as **bs58**
 * (same alphabet as upstream `darkirc` settings parsing).
 */
internal object DarkircBs58 {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private const val SECRET_LEN = 32

    fun decode32OrNull(input: String): ByteArray? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return null
        }
        return runCatching { decode(trimmed) }.getOrNull()?.takeIf { it.size == SECRET_LEN }
    }

    fun isValidSecret32(input: String): Boolean = decode32OrNull(input) != null

    private fun decode(input: String): ByteArray {
        val indexes =
            IntArray(input.length) { i ->
                val idx = ALPHABET.indexOf(input[i])
                if (idx < 0) {
                    error("invalid base58 character at $i")
                }
                idx
            }
        var zeros = 0
        while (zeros < input.length && input[zeros] == '1') {
            zeros++
        }
        val size = ((input.length - zeros) * 733 / 1000) + 1
        val b58 = ByteArray(size)
        var length = 0
        for (c in indexes) {
            var carry = c
            var i = 0
            while (i < size) {
                carry += ALPHABET.length * (b58[i].toInt() and 0xff)
                b58[i] = (carry % 256).toByte()
                carry /= 256
                i++
            }
            length = i
        }
        var it = size - length
        while (it < size && b58[it] == 0.toByte()) {
            it++
        }
        val decoded = ByteArray(zeros + (size - it))
        var j = zeros
        while (it < size) {
            decoded[j++] = b58[it++]
        }
        return decoded
    }
}
