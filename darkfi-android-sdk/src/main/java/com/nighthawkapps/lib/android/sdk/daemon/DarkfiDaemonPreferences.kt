package com.nighthawkapps.lib.android.sdk.daemon

import android.content.Context

/**
 * Daemon lifecycle prefs (embedded darkfid / darkirc), separate from chat transport copy.
 */
class DarkfiDaemonPreferences(
    context: Context,
) {
    private val sp =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * When true (default), start packaged **darkfid** so wallet `drk` can reach loopback JSON-RPC
     * (`127.0.0.1:18345` testnet) without a desktop node — mirrors embedded darkirc.
     */
    var runEmbeddedDarkfid: Boolean
        get() = sp.getBoolean(KEY_EMBEDDED_DARKFID, true)
        set(value) {
            sp.edit().putBoolean(KEY_EMBEDDED_DARKFID, value).apply()
        }

    companion object {
        private const val PREFS_NAME = "darkfi_daemon_prefs"
        private const val KEY_EMBEDDED_DARKFID = "darkfi_run_embedded_darkfid"
    }
}
