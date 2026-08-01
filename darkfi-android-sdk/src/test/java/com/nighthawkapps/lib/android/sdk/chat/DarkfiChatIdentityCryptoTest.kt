package com.nighthawkapps.lib.android.sdk.chat

import kotlin.test.Test
import kotlin.test.assertEquals

class DarkfiChatIdentityCryptoTest {
    @Test
    fun publicIdHex_stableForEntropy() {
        val entropy = ByteArray(16) { it.toByte() }
        val a = DarkfiChatIdentityCrypto.publicIdHex(entropy)
        val b = DarkfiChatIdentityCrypto.publicIdHex(entropy)
        assertEquals(64, a.length)
        assertEquals(a, b)
    }

    @Test
    fun publicIdHex_emptyEntropyStillDeterministic() {
        val a = DarkfiChatIdentityCrypto.publicIdHex(ByteArray(0))
        val b = DarkfiChatIdentityCrypto.publicIdHex(ByteArray(0))
        assertEquals(64, a.length)
        assertEquals(a, b)
    }
}
