package com.nighthawkapps.lib.android.sdk.chat.darkirc

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatPreferences
import com.nighthawkapps.lib.android.spackle.Twig

object DarkircDaemonBootstrap {
    @JvmStatic
    fun maybeStart(context: Context) {
        val prefs = DarkfiChatPreferences(context.applicationContext)
        if (!prefs.runEmbeddedDarkirc) {
            return
        }
        if (!DarkircEmbeddedRunner.hasBundledBinary(context)) {
            Twig.info { "Embedded darkirc: no binary packaged for this ABI — skipped" }
            return
        }
        try {
            ContextCompat.startForegroundService(
                context.applicationContext,
                Intent(context.applicationContext, DarkircDaemonService::class.java),
            )
        } catch (e: Exception) {
            Twig.error(e) { "Embedded darkirc: could not start foreground service" }
        }
    }

    /**
     * Starts the foreground service (keep-alive) and synchronously aligns `irc_listen` in
     * [DarkircEmbeddedRunner.ensureConfigFile] with IRC prefs before spawning (or restarting) the daemon.
     */
    @JvmStatic
    fun prepareForLoopbackIrc(context: Context): Boolean {
        val app = context.applicationContext
        val prefs = DarkfiChatPreferences(app)
        if (!prefs.runEmbeddedDarkirc || !DarkircEmbeddedRunner.hasBundledBinary(app)) {
            return false
        }
        maybeStart(app)
        return DarkircEmbeddedRunner.syncEmbeddedIrcListenConfigAndEnsureRunning(app)
    }

    /**
     * After long background / process death recovery, reconcile config and try to resurrect the bundled
     * daemon without blocking the UI thread. IRC connect path still probes TCP before opening a session.
     */
    @JvmStatic
    fun warmEmbeddedOnForeground(context: Context) {
        val app = context.applicationContext
        val prefs = DarkfiChatPreferences(app)
        if (!prefs.runEmbeddedDarkirc || !DarkircEmbeddedRunner.hasBundledBinary(app)) {
            return
        }
        maybeStart(app)
        Thread(
            {
                DarkircEmbeddedRunner.syncEmbeddedIrcListenConfigAndEnsureRunning(app)
            },
            "darkirc-fg-resume",
        ).start()
    }

    /** Stops the service/process, rewrites config, and spawns again (Settings → restart embedded node). */
    @JvmStatic
    fun restartEmbeddedLoopback(context: Context): Boolean {
        val app = context.applicationContext
        stop(app)
        return prepareForLoopbackIrc(app)
    }

    @JvmStatic
    fun stop(context: Context) {
        val app = context.applicationContext
        app.stopService(Intent(app, DarkircDaemonService::class.java))
        DarkircEmbeddedRunner.stop()
    }
}
