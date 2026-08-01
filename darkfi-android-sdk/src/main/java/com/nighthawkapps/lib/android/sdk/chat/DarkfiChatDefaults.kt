package com.nighthawkapps.lib.android.sdk.chat

/**
 * Defaults aligned with **`bin/darkirc`** (**`darkirc_config.toml`**; **`docs/darkfi-chat-upstream.md`**).
 *
 * Covers IRC listen (**`tcp://127.0.0.1:6667`**), **`[net.profiles]`** seeds, **`magic_bytes`**,
 * and **`autojoin`** channels — re-diff when **`docs/upstream/darkfi-revision.txt`** bumps.
 *
 * ChaCha DM/channel crypto is implemented in DarkfiChatCrypto.kt.
 */
object DarkfiChatDefaults {
    const val LOG_TAG = "DarkfiChat"

    /** Matches `p2p_settings.magic_bytes` assignment in `darkirc.rs`. */
    val MAGIC_BYTES: ByteArray = byteArrayOf(0xFB.toByte(), 0xE5.toByte(), 0xC7.toByte(), 0xB5.toByte())

    const val P2P_APP_NAME: String = "darkirc"

    /** Upstream default `#dags_count` (each DAG ≈ 1h message history, max 24). */
    const val DEFAULT_DAGS_COUNT: Int = 8
    const val MIN_DAGS_COUNT: Int = 1
    const val MAX_DAGS_COUNT: Int = 24

    /** Same ordering as **`autojoin`** in upstream `darkirc_config.toml`. */
    val DEFAULT_PUBLIC_CHANNELS: List<String> =
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

    /**
     * Default IRC topics from upstream `[channel."#…"]` blocks in `darkirc_config.toml`.
     * `#hackers` is in **`autojoin`** but has no dedicated `[channel]` block upstream.
     */
    val DEFAULT_CHANNEL_TOPICS: Map<String, String> =
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

    /**
     * Clearnet darkirc P2P seeds (upstream `darkirc_config.toml`
     * `[net.profiles."tcp+tls"]` at tip 57397a9e0; network moved to :9600 in
     * July 2026).
     */
    val CLEARNET_SEEDS: List<String> =
        listOf(
            "tcp+tls://lilith0.dark.fi:9600",
            "tcp+tls://lilith1.dark.fi:9600",
        )

    val TOR_SEEDS: List<String> =
        listOf(
            "tor://wgxxaifz5gv4iggcflyl67lgmsihffs6bbwobqah4np52t3y3olrnpid.onion:9601",
            "tor://inx5s3pdzddvgb5ii3oydutmbvw6fvor3oqu65wtxl3pyevtvrdn4had.onion:9601",
        )
}
