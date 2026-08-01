package com.nighthawkapps.lib.android.sdk.wallet.darkfid

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.nighthawkapps.lib.android.sdk.daemon.DarkfiDaemonPreferences
import com.nighthawkapps.lib.android.spackle.Twig

/** Starts packaged **darkfid** so in-process `drk` can use loopback JSON-RPC (upstream `DrkPlugin`). */
object DarkfidDaemonBootstrap {
    @JvmStatic
    fun maybeStart(context: Context) {
        val prefs = DarkfiDaemonPreferences(context.applicationContext)
        if (!prefs.runEmbeddedDarkfid) {
            return
        }
        if (!DarkfidEmbeddedRunner.hasBundledBinary(context)) {
            Twig.info { "Embedded darkfid: no binary packaged — wallet needs external node or adb reverse" }
            return
        }
        try {
            ContextCompat.startForegroundService(
                context.applicationContext,
                Intent(context.applicationContext, DarkfidDaemonService::class.java),
            )
        } catch (e: IllegalStateException) {
            Twig.error(e) { "Embedded darkfid: could not start foreground service" }
        }
    }

    /** Ensures config + process before wallet RPC probe (blocking caller thread). */
    @JvmStatic
    fun prepareLoopbackRpc(context: Context): Boolean {
        val app = context.applicationContext
        val prefs = DarkfiDaemonPreferences(app)
        if (!prefs.runEmbeddedDarkfid || !DarkfidEmbeddedRunner.hasBundledBinary(app)) {
            return false
        }
        maybeStart(app)
        return DarkfidEmbeddedRunner.syncConfigAndEnsureRunning(app)
    }

    @JvmStatic
    fun warmOnForeground(context: Context) {
        val app = context.applicationContext
        val prefs = DarkfiDaemonPreferences(app)
        if (!prefs.runEmbeddedDarkfid || !DarkfidEmbeddedRunner.hasBundledBinary(app)) {
            return
        }
        maybeStart(app)
        Thread(
            { DarkfidEmbeddedRunner.syncConfigAndEnsureRunning(app) },
            "darkfid-fg-resume",
        ).start()
    }

    @JvmStatic
    fun restartEmbedded(context: Context): Boolean {
        stop(context)
        return prepareLoopbackRpc(context)
    }

    @JvmStatic
    fun stop(context: Context) {
        val app = context.applicationContext
        app.stopService(Intent(app, DarkfidDaemonService::class.java))
        DarkfidEmbeddedRunner.stop()
    }
}
