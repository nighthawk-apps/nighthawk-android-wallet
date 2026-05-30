package com.nighthawkwallet.android

import com.nighthawkapps.lib.android.sdk.chat.darkirc.DarkircDaemonBootstrap
import com.nighthawkapps.lib.android.sdk.chat.darkirc.DarkircEmbeddedResumeCoordinator
import com.nighthawkapps.lib.android.spackle.StrictModeCompat
import com.nighthawkapps.lib.android.spackle.Twig

@Suppress("unused")
class NighthawkWalletApplication : CoroutineApplication() {
    override fun onCreate() {
        super.onCreate()

        configureLogging()

        configureStrictMode()

        DarkircDaemonBootstrap.maybeStart(this)
        DarkircEmbeddedResumeCoordinator.register(this)
    }

    private fun configureLogging() {
        Twig.initialize(applicationContext, loggingEnabled = BuildConfig.LOGCAT_ENABLED)
        Twig.debug { "Starting application…" }

        // Mainnet release sets LOGCAT_ENABLED=false — skip assert because Twig calls are intentionally
        // retained (no-op).
        if (!BuildConfig.DEBUG && BuildConfig.LOGCAT_ENABLED) {
            // In minified release builds with logcat on, Twig noise should be stripped by R8 consumer rules.
            Twig.assertLoggingStripped()
        }
    }

    private fun configureStrictMode() {
        if (BuildConfig.DEBUG) {
            StrictModeCompat.enableStrictMode(BuildConfig.IS_STRICT_MODE_CRASH_ENABLED)
        }
    }
}
