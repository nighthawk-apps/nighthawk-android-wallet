package com.nighthawkapps.lib.android.sdk.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LightwalletTlsPinTest {
    @Test
    fun parseHexPin_accepts64Hex() {
        val hex = "a".repeat(64)
        val bytes = LightwalletTlsPin.parseHexPin(hex)!!
        assertEquals(32, bytes.size)
        assertEquals(0xAAu.toUByte(), bytes[0])
    }

    @Test
    fun parseHexPin_stripsColonsAndPrefix() {
        val hex = "0x" + (0 until 32).joinToString(":") { "bb" }
        val bytes = LightwalletTlsPin.parseHexPin(hex)!!
        assertEquals(32, bytes.size)
        assertEquals(0xBBu.toUByte(), bytes[0])
    }

    @Test
    fun parseHexPin_rejectsBadLength() {
        assertNull(LightwalletTlsPin.parseHexPin("abcd"))
        assertNull(LightwalletTlsPin.parseHexPin(null))
        assertNull(LightwalletTlsPin.parseHexPin(""))
        assertNull(LightwalletTlsPin.parseHexPin("zz".repeat(32)))
    }
}
