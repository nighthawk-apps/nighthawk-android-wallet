package com.nighthawkwallet.android

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.nighthawkapps.lib.android.global.AppDaemonCoordinator
import com.nighthawkapps.lib.android.sdk.net.TransportLifecycle
import com.nighthawkapps.lib.android.sdk.nym.AppNymCoordinator
import com.nighthawkapps.lib.android.sdk.tor.AppTorCoordinator
import com.nighthawkapps.lib.android.sdk.wallet.darkfid.DarkfidDaemonBootstrap
import com.nighthawkapps.lib.android.spackle.StrictModeCompat
import com.nighthawkapps.lib.android.spackle.Twig

@Suppress("unused")
class NighthawkWalletApplication : CoroutineApplication() {
    override fun onCreate() {
        super.onCreate()

        configureLogging()

        configureStrictMode()

        TransportLifecycle.install(this)
        // Register coordinator without booting daemons/FGS during background WorkManager
        // or BroadcastReceiver process wakeups; daemons start on foreground ON_START.
        AppDaemonCoordinator.install(this, autoStart = false)
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    AppNymCoordinator.warmStartIfEnabled(this@NighthawkWalletApplication)
                    AppTorCoordinator.warmStartIfEnabled(this@NighthawkWalletApplication)
                    DarkfidDaemonBootstrap.maybeStart(this@NighthawkWalletApplication)
                    AppDaemonCoordinator.get().onForeground()
                }

                override fun onStop(owner: LifecycleOwner) {
                    AppDaemonCoordinator.get().onBackground()
                    com.nighthawkapps.lib.android.global.AppWalletCoordinator
                        .setUserAuthenticated(false)
                }
            },
        )
    }

    private fun configureLogging() {
        Twig.initialize(applicationContext, loggingEnabled = BuildConfig.LOGCAT_ENABLED)
        Twig.debug { "Starting application…" }

        // Release builds set LOGCAT_ENABLED=false — skip assert because Twig calls are intentionally
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
