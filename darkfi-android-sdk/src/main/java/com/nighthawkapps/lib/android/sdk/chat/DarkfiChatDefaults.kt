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

    val CLEARNET_SEEDS: List<String> =
        listOf(
            "tcp+tls://lilith0.dark.fi:25551",
            "tcp+tls://lilith1.dark.fi:25551",
        )

    val TOR_SEEDS: List<String> =
        listOf(
            "tor://g7fxelebievvpr27w7gt24lflptpw3jeeuvafovgliq5utdst6xyruyd.onion:25552",
            "tor://yvklzjnfmwxhyodhrkpomawjcdvcaushsj6torjz2gyd7e25f3gfunyd.onion:25552",
        )
}
