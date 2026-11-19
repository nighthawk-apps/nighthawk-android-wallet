package com.nighthawkapps.lib.android.sdk.net

import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatPreferences

/**
 * Process-local privacy route. "Continue without Nym" forces Direct until the
 * process ends or the user picks a network in settings. It does not write prefs.
 */
object SessionTransport {
    @Volatile
    private var sessionDirect: Boolean = false

    fun isSessionDirect(): Boolean = sessionDirect

    fun effective(prefs: DarkfiChatPreferences): PrivacyNetwork =
        if (sessionDirect) PrivacyNetwork.Direct else prefs.privacyNetwork

    fun continueWithoutNym() {
        sessionDirect = true
        TransportNative.continueWithoutNym()
    }

    /** User picked a network in settings. The stored preference is authoritative again. */
    fun clear() {
        sessionDirect = false
        TransportNative.clearSessionOverride()
    }
}
