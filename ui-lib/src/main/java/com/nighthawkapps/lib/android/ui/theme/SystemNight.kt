package com.nighthawkapps.lib.android.ui.theme

import android.app.Application
import android.content.ComponentCallbacks
import android.content.res.Configuration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

fun Application.isSystemInNightMode(): Boolean {
    val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return night == Configuration.UI_MODE_NIGHT_YES
}

/** Emits the current night-mode bit, then again whenever the system theme changes. */
fun Application.systemNightMode(): Flow<Boolean> =
    callbackFlow {
        trySend(isSystemInNightMode())
        val callbacks =
            object : ComponentCallbacks {
                override fun onConfigurationChanged(newConfig: Configuration) {
                    val night = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    trySend(night == Configuration.UI_MODE_NIGHT_YES)
                }

                override fun onLowMemory() = Unit
            }
        registerComponentCallbacks(callbacks)
        awaitClose { unregisterComponentCallbacks(callbacks) }
    }.distinctUntilChanged()
