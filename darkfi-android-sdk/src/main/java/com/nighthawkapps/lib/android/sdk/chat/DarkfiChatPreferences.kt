package com.nighthawkapps.lib.android.sdk.chat

import android.content.Context
import com.nighthawkapps.lib.android.sdk.chat.darkirc.DarkircRuntimeSettings

/**
 * Outbound transport: Tor SOCKS for wallet HTTP clients (e.g. price APIs) and for DarkIRC when
 * the IRC host is not loopback. Same preference store as chat settings.
 *
 * Modeled after common Android “route app through local proxy” patterns (e.g. Guardian NetCipher);
 * Nym’s `nym-vpn-android` uses a `VpnService` tunnel instead of a local SOCKS listener.
 */
class DarkfiChatPreferences(
    context: Context
) {
    private val app = context.applicationContext
    private val sp = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * When true, clearnet HTTP(S) clients in the Android app (OkHttp/Retrofit) use SOCKS
     * ([socksHost]:[socksPort], default loopback). Also gates DarkIRC SOCKS for non-loopback hosts.
     */
    var routeOutboundThroughTor: Boolean
        get() = sp.getBoolean(KEY_TOR, true)
        set(value) {
            sp.edit().putBoolean(KEY_TOR, value).apply()
        }

    /**
     * When true, wallet sync is UnifOMR-only (no supplemental trial decrypt).
     * Default true. Turn off in Advanced Settings to receive from `drk`.
     */
    var strictOmrOnly: Boolean
        get() = sp.getBoolean(KEY_STRICT_OMR_ONLY, true)
        set(value) {
            sp.edit().putBoolean(KEY_STRICT_OMR_ONLY, value).apply()
        }

    /** Synonym for [routeOutboundThroughTor] (historic “chat toggle” wording). */
    var useTorForChat: Boolean
        get() = routeOutboundThroughTor
        set(value) {
            routeOutboundThroughTor = value
        }

    var ircServerHost: String
        get() = sp.getString(KEY_IRC_HOST, DEFAULT_HOST) ?: DEFAULT_HOST
        set(value) {
            sp.edit().putString(KEY_IRC_HOST, value.ifBlank { DEFAULT_HOST }).apply()
        }

    var ircServerPort: Int
        get() =
            sp.getInt(KEY_IRC_PORT, DEFAULT_PORT).takeIf { it in 1..65535 }
                ?: DEFAULT_PORT
        set(value) {
            sp.edit().putInt(KEY_IRC_PORT, value.coerceIn(1, 65535)).apply()
        }

    var ircPassword: String?
        get() =
            DarkfiChatSecureStore
                .getString(app, DarkfiChatSecureStore.KEY_IRC_PASSWORD)
                ?.takeIf { it.isNotBlank() }
        set(value) {
            if (value.isNullOrBlank()) {
                DarkfiChatSecureStore.remove(app, DarkfiChatSecureStore.KEY_IRC_PASSWORD)
            } else {
                DarkfiChatSecureStore.putString(app, DarkfiChatSecureStore.KEY_IRC_PASSWORD, value)
            }
        }

    /**
     * SOCKS5 address when Tor routing applies (defaults match bundled tor-android).
     */
    var socksHost: String
        get() =
            sp.getString(KEY_SOCKS_HOST, TorIntegrationHelper.DEFAULT_SOCKS_HOST)
                ?: TorIntegrationHelper.DEFAULT_SOCKS_HOST
        set(value) {
            val cleaned = value.trim().ifBlank { TorIntegrationHelper.DEFAULT_SOCKS_HOST }
            sp.edit().putString(KEY_SOCKS_HOST, cleaned).apply()
        }

    var socksPort: Int
        get() =
            sp.getInt(KEY_SOCKS_PORT, TorIntegrationHelper.DEFAULT_SOCKS_PORT).takeIf { it in 1..65535 }
                ?: TorIntegrationHelper.DEFAULT_SOCKS_PORT
        set(value) {
            sp.edit().putInt(KEY_SOCKS_PORT, value.coerceIn(1, 65535)).apply()
        }

    /**
     * When true (default), the app runs Guardian `tor-android` in-process for SOCKS. When false, only
     * an external SOCKS at [socksHost]:[socksPort] is used (you must run Tor or another SOCKS proxy yourself).
     * Default matches iOS embedded Tor (`useEmbeddedTor = true`).
     */
    var useEmbeddedTor: Boolean
        get() = sp.getBoolean(KEY_EMBEDDED_TOR, true)
        set(value) {
            sp.edit().putBoolean(KEY_EMBEDDED_TOR, value).apply()
        }

    /**
     * When true (default), start packaged **darkirc** so the IRC client can reach `127.0.0.1:6667`
     * without a desktop (foreground service).
     */
    var runEmbeddedDarkirc: Boolean
        get() = sp.getBoolean(KEY_EMBEDDED_DARKIRC, true)
        set(value) {
            sp.edit().putBoolean(KEY_EMBEDDED_DARKIRC, value).apply()
        }

    /**
     * How many DAG hours of message history to sync (upstream `dags_count`, 1–24).
     */
    var darkircDagsCount: Int
        get() =
            sp.getInt(KEY_DARKIRC_DAGS_COUNT, DarkfiChatDefaults.DEFAULT_DAGS_COUNT).coerceIn(
                DarkfiChatDefaults.MIN_DAGS_COUNT,
                DarkfiChatDefaults.MAX_DAGS_COUNT,
            )
        set(value) {
            sp
                .edit()
                .putInt(
                    KEY_DARKIRC_DAGS_COUNT,
                    value.coerceIn(DarkfiChatDefaults.MIN_DAGS_COUNT, DarkfiChatDefaults.MAX_DAGS_COUNT),
                ).apply()
        }

    /**
     * Upstream `fast_mod`: skip full history fetch for faster DAG sync (header-only mode).
     */
    var darkircFastMode: Boolean
        get() = sp.getBoolean(KEY_DARKIRC_FAST_MODE, false)
        set(value) {
            sp.edit().putBoolean(KEY_DARKIRC_FAST_MODE, value).apply()
        }

    /** Event-graph debug replay (upstream `replay_mode`); off by default on mobile. */
    var darkircReplayMode: Boolean
        get() = sp.getBoolean(KEY_DARKIRC_REPLAY_MODE, false)
        set(value) {
            sp.edit().putBoolean(KEY_DARKIRC_REPLAY_MODE, value).apply()
        }

    fun darkircRuntimeSettings(): DarkircRuntimeSettings =
        DarkircRuntimeSettings(
            dagsCount = darkircDagsCount,
            fastMode = darkircFastMode,
            replayMode = darkircReplayMode,
            ircServerPassword = ircPassword,
        ).normalized()

    /**
     * Persisted IRC nickname (generated once via [ensureIrcNickname] when blank).
     */
    var ircNickname: String
        get() = sp.getString(KEY_IRC_NICK, "")!!.trim()
        set(value) {
            sp.edit().putString(KEY_IRC_NICK, value.trim()).apply()
        }

    fun ensureIrcNickname(): String {
        var nick = ircNickname
        if (nick.isBlank()) {
            nick = "hawk${(100..999).random()}"
            ircNickname = nick
        }
        return sanitizeNickname(nick)
    }

    companion object {
        private const val PREFS_NAME = "darkfi_chat_transport"
        private const val KEY_TOR = "prefer_tor_like_use_tor_txt"
        private const val KEY_STRICT_OMR_ONLY = "darkfi_strict_omr_only"
        private const val KEY_IRC_HOST = "darkfi_irc_host"
        private const val KEY_IRC_PORT = "darkfi_irc_port"
        private const val KEY_IRC_NICK = "darkfi_irc_nickname"
        private const val KEY_SOCKS_HOST = "darkfi_socks_host"
        private const val KEY_SOCKS_PORT = "darkfi_socks_port"
        private const val KEY_EMBEDDED_TOR = "darkfi_use_embedded_tor_android"
        private const val KEY_EMBEDDED_DARKIRC = "darkfi_run_embedded_darkirc"
        private const val KEY_DARKIRC_DAGS_COUNT = "darkfi_darkirc_dags_count"
        private const val KEY_DARKIRC_FAST_MODE = "darkfi_darkirc_fast_mod"
        private const val KEY_DARKIRC_REPLAY_MODE = "darkfi_darkirc_replay_mode"

        private const val DEFAULT_HOST = "127.0.0.1"
        private const val DEFAULT_PORT = 6667

        fun sanitizeNickname(raw: String): String {
            val cleaned =
                raw.filter { it.isLetterOrDigit() || it == '_' }.take(DarkircLimits.MAX_NICK_LEN)
            return cleaned.ifBlank { "hawk${(100..999).random()}" }
        }
    }
}
