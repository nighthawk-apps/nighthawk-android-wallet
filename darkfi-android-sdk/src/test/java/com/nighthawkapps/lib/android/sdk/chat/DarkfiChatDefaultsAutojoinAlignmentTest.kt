package com.nighthawkapps.lib.android.sdk.chat

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards **`darkirc_config.toml`** **`autojoin`** alignment (`docs/darkfi-integration.md`, Upstream parity status).
 */
class DarkfiChatDefaultsAutojoinAlignmentTest {
    @Test
    fun defaultPublicChannels_matchesUpstreamAutojoinOrderThroughLunardao() {
        assertEquals(
            listOf(
                "#dev",
                "#media",
                "#hackers",
                "#memes",
                "#philosophy",
                "#markets",
                "#math",
                "#random",
                "#lunardao",
            ),
            DarkfiChatDefaults.DEFAULT_PUBLIC_CHANNELS,
        )
    }

    @Test
    fun magicBytes_matchUpstreamDarkircConfigTomlNetSection() {
        assertContentEquals(
            byteArrayOf(0xFB.toByte(), 0xE5.toByte(), 0xC7.toByte(), 0xB5.toByte()),
            DarkfiChatDefaults.MAGIC_BYTES,
        )
    }

    @Test
    fun seedLiterals_includeClearnetAndTorProfilesFromUpstreamSampleConfig() {
        assertTrue(DarkfiChatDefaults.CLEARNET_SEEDS.any { it.contains("lilith0.dark.fi") })
        assertTrue(DarkfiChatDefaults.TOR_SEEDS.any { it.contains(".onion") })
    }
}
