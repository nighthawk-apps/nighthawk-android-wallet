package com.nighthawkapps.lib.android.sdk.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DarkfiChatSeedParseTest {
    @Test
    fun extractHost_clearnetTls() {
        assertEquals(
            "lilith0.dark.fi",
            DarkfiChatController.extractHostFromSeed("tcp+tls://lilith0.dark.fi:25551"),
        )
    }

    @Test
    fun extractHost_torOnion() {
        assertEquals(
            "g7fxelebievvpr27w7gt24lflptpw3jeeuvafovgliq5utdst6xyruyd.onion",
            DarkfiChatController.extractHostFromSeed(
                "tor://g7fxelebievvpr27w7gt24lflptpw3jeeuvafovgliq5utdst6xyruyd.onion:25552",
            ),
        )
    }

    @Test
    fun extractHost_invalid() {
        assertNull(DarkfiChatController.extractHostFromSeed("not-a-url"))
    }
}
