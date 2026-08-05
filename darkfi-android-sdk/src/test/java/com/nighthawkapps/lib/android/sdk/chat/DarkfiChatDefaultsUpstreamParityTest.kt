package com.nighthawkapps.lib.android.sdk.chat

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards [DarkfiChatDefaults] against pinned upstream `bin/darkirc/darkirc_config.toml`
 * (`docs/upstream/darkfi-revision.txt`).
 */
class DarkfiChatDefaultsUpstreamParityTest {
    @Test
    fun autojoinMatchesPinnedDarkircConfig() {
        val expected =
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
            )
        assertEquals(expected, DarkfiChatDefaults.DEFAULT_PUBLIC_CHANNELS)
    }

    @Test
    fun clearnetSeedsIncludeLilithSeeds() {
        assertTrue(DarkfiChatDefaults.CLEARNET_SEEDS.contains("tcp+tls://lilith0.dark.fi:9600"))
        assertTrue(DarkfiChatDefaults.CLEARNET_SEEDS.contains("tcp+tls://lilith1.dark.fi:9600"))
    }

    @Test
    fun torSeedsMatchTorProfile() {
        val expected =
            listOf(
                "tor://wgxxaifz5gv4iggcflyl67lgmsihffs6bbwobqah4np52t3y3olrnpid.onion:9601",
                "tor://inx5s3pdzddvgb5ii3oydutmbvw6fvor3oqu65wtxl3pyevtvrdn4had.onion:9601",
            )
        assertEquals(expected, DarkfiChatDefaults.TOR_SEEDS)
    }

    @Test
    fun magicBytesMatchDarkircNetSection() {
        val expected = byteArrayOf(0xFB.toByte(), 0xE5.toByte(), 0xC7.toByte(), 0xB5.toByte())
        assertContentEquals(expected, DarkfiChatDefaults.MAGIC_BYTES)
    }

    @Test
    fun defaultChannelTopicsMatchPinnedDarkircConfig() {
        // Upstream `[channel."#…"]` topic blocks only — `#hackers` has none.
        val upstreamTopics =
            mapOf(
                "#dev" to "DarkFi Development HQ",
                "#media" to "DarkFi Art, Fashion, Video, Memetics",
                "#memes" to "DarkFi Meme Reality",
                "#philosophy" to "Philosophy Discussions",
                "#markets" to "Crypto Market Talk",
                "#math" to "Math Talk",
                "#random" to "/b/",
                "#lunardao" to "LunarDAO talk",
            )
        for ((channel, topic) in upstreamTopics) {
            assertEquals(topic, DarkfiChatDefaults.DEFAULT_CHANNEL_TOPICS[channel], channel)
        }
        // Intentional Nighthawk fill for autojoin channel with no upstream topic.
        assertEquals("Hacker Culture", DarkfiChatDefaults.DEFAULT_CHANNEL_TOPICS["#hackers"])
        assertEquals(upstreamTopics.size + 1, DarkfiChatDefaults.DEFAULT_CHANNEL_TOPICS.size)
    }
}
