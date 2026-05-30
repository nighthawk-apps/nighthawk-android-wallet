package com.nighthawkapps.lib.android.sdk.chat

import android.content.Context

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
        get() = sp.getBoolean(KEY_TOR, false)
        set(value) {
            sp.edit().putBoolean(KEY_TOR, value).apply()
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
                .sharedPreferences(app)
                .getString(DarkfiChatSecureStore.KEY_IRC_PASSWORD, null)
                ?.takeIf { it.isNotBlank() }
        set(value) {
            val enc = DarkfiChatSecureStore.sharedPreferences(app)
            val ed = enc.edit()
            if (value.isNullOrBlank()) {
                ed.remove(DarkfiChatSecureStore.KEY_IRC_PASSWORD)
            } else {
                ed.putString(DarkfiChatSecureStore.KEY_IRC_PASSWORD, value)
            }
            ed.apply()
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
            nick = "hawk${(1000..9999).random()}"
            ircNickname = nick
        }
        return sanitizeNickname(nick)
    }

    companion object {
        private const val PREFS_NAME = "darkfi_chat_transport"
        private const val KEY_TOR = "prefer_tor_like_use_tor_txt"
        private const val KEY_IRC_HOST = "darkfi_irc_host"
        private const val KEY_IRC_PORT = "darkfi_irc_port"
        private const val KEY_IRC_NICK = "darkfi_irc_nickname"
        private const val KEY_SOCKS_HOST = "darkfi_socks_host"
        private const val KEY_SOCKS_PORT = "darkfi_socks_port"
        private const val KEY_EMBEDDED_TOR = "darkfi_use_embedded_tor_android"
        private const val KEY_EMBEDDED_DARKIRC = "darkfi_run_embedded_darkirc"

        private const val DEFAULT_HOST = "127.0.0.1"
        private const val DEFAULT_PORT = 6667

        fun sanitizeNickname(raw: String): String {
            val cleaned =
                raw.filter { it.isLetterOrDigit() || it == '_' }.take(DarkircLimits.MAX_NICK_LEN)
            return cleaned.ifBlank { "hawk${(1000..9999).random()}" }
        }
    }
}
