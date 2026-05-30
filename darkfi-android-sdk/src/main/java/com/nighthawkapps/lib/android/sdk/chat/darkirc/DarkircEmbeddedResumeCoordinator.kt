package com.nighthawkapps.lib.android.sdk.chat.darkirc

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * Re-triggers bundled darkirc when Android brings the UI process forward again so the IRC listener has
 * a chance to restart after eviction or prolonged background.
 */
object DarkircEmbeddedResumeCoordinator {
    @JvmStatic
    fun register(application: Application) {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    DarkircDaemonBootstrap.warmEmbeddedOnForeground(application)
                }
            },
        )
    }
}
