package com.nighthawkapps.lib.android.ui.preference

import com.nighthawkapps.lib.android.preference.model.entry.BooleanPreferenceDefault
import com.nighthawkapps.lib.android.preference.model.entry.PreferenceKey
import com.nighthawkapps.lib.android.preference.model.entry.StringPreferenceDefault

object StandardPreferenceKeys {
    /**
     * Whether the user has completed the backup flow for a newly created wallet.
     */
    val IS_USER_BACKUP_COMPLETE = BooleanPreferenceDefault(PreferenceKey("is_user_backup_complete"), false)

    // Analytics toggle defaults off until product wiring confirms retention requirements.
    val IS_ANALYTICS_ENABLED = BooleanPreferenceDefault(PreferenceKey("is_analytics_enabled"), false)

    val IS_BACKGROUND_SYNC_ENABLED = BooleanPreferenceDefault(PreferenceKey("is_background_sync_enabled"), true)

    val IS_KEEP_SCREEN_ON_DURING_SYNC = BooleanPreferenceDefault(PreferenceKey("is_keep_screen_on_during_sync"), true)

    // Removed: last_entered_pin (C1 plaintext PIN leftover). Do not reintroduce.

    val IS_TOUCH_ID_OR_FACE_ID_ENABLED = BooleanPreferenceDefault(PreferenceKey("is_touch_id_face_id_enabled"), false)

    val IS_UNSTOPPABLE_SERVICE_ENABLED = BooleanPreferenceDefault(PreferenceKey("is_unstoppable_service_enabled"), false)

    val IS_NAVIGATE_AWAY_FROM_APP_WARNING_SHOWN = BooleanPreferenceDefault(PreferenceKey("is_navigate_from_app_warning_shown"), false)

    val IS_BANDIT_AVAILABLE = BooleanPreferenceDefault(PreferenceKey("is_bandit_available"), false)

    /** Nighthawk Mesh (Beta). Off by default; radios start only when the user enables this. */
    val IS_NIGHTHAWK_MESH_ENABLED = BooleanPreferenceDefault(PreferenceKey("is_nighthawk_mesh_enabled"), false)

    /** Local-only per-thread chat labels. Not a global public ID. */
    val CHAT_THREAD_DISPLAY_NAMES = StringPreferenceDefault(PreferenceKey("chat_thread_display_names_json"), "")

    /**
     * Keep BLE / connectedDevice running in the background while mesh is on.
     * Default on; turning it off stops scanning when the app is backgrounded.
     */
    val IS_NIGHTHAWK_MESH_ALWAYS_ON = BooleanPreferenceDefault(PreferenceKey("is_nighthawk_mesh_always_on"), true)

    /**
     * Share this phone's internet with nearby Nighthawk peers (gateway + Wi-Fi bulk).
     * Default on. The engine still arms only while charging on unmetered Wi-Fi.
     */
    val IS_NIGHTHAWK_MESH_GATEWAY = BooleanPreferenceDefault(PreferenceKey("is_nighthawk_mesh_gateway"), true)

    val PREFERRED_LOGO = StringPreferenceDefault(PreferenceKey("preferred_logo"), "0")

    val IS_DARK_THEME_ENABLED = BooleanPreferenceDefault(PreferenceKey("is_dark_theme_enabled"), true)

    /** Stored theme mode; blank falls back to legacy [IS_DARK_THEME_ENABLED]. */
    val APP_THEME_VARIANT = StringPreferenceDefault(PreferenceKey("app_theme_variant"), "")

    /**
     * The fiat currency that the user prefers.
     */
    val PREFERRED_FIAT_CURRENCY = FiatCurrencyPreferenceDefault(PreferenceKey("preferred_fiat_currency_code"))
}
