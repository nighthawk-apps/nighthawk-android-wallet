package com.nighthawkapps.lib.android.preference.model.entry

/**
 * Deliberately silent: coercion failures fall back to defaults without printing (Android would route
 * [println] to logcat via `System.out`, bypassing production log toggles).
 */
@Suppress("UNUSED_PARAMETER")
internal fun logPreferenceCoercionFailure(
    kind: String,
    key: PreferenceKey,
    raw: String,
    cause: Throwable,
) {
    // Parameters retained for clearer callsites/breakpoints; do not emit raw preference material.
}
