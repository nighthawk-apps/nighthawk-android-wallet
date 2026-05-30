package com.nighthawkapps.lib.android.spackle.model

import kotlin.test.Test
import kotlin.test.assertFailsWith

class IndexTest {
    @Test
    fun out_of_bounds() {
        assertFailsWith(IllegalArgumentException::class) {
            Index(-1)
        }
    }
}
