package com.nighthawkapps.lib.android.sdk.chat.darkirc

import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatDefaults

/**
 * Upstream `darkirc_config.toml` runtime knobs (see pinned `bin/darkirc/darkirc_config.toml`).
 */
data class DarkircRuntimeSettings(
    val dagsCount: Int = DarkfiChatDefaults.DEFAULT_DAGS_COUNT,
    val fastMode: Boolean = false,
    val replayMode: Boolean = false,
    val ircServerPassword: String? = null,
) {
    fun normalized(): DarkircRuntimeSettings =
        copy(
            dagsCount = dagsCount.coerceIn(DarkfiChatDefaults.MIN_DAGS_COUNT, DarkfiChatDefaults.MAX_DAGS_COUNT),
            ircServerPassword = ircServerPassword?.trim()?.takeIf { it.isNotEmpty() },
        )
}
