package com.nighthawkapps.lib.android.sdk.net

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import java.util.concurrent.atomic.AtomicBoolean

/** Process foreground/background and default-network changes for the shared route. */
object TransportLifecycle {
    private val installed = AtomicBoolean(false)

    fun install(app: Application) {
        if (!installed.compareAndSet(false, true)) return
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> TransportNative.onForeground()
                    Lifecycle.Event.ON_STOP -> TransportNative.onBackground()
                    else -> Unit
                }
            },
        )
        val manager = app.getSystemService(ConnectivityManager::class.java) ?: return
        val request =
            NetworkRequest
                .Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
        runCatching {
            manager.registerNetworkCallback(
                request,
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        TransportNative.onNetworkChanged()
                    }

                    override fun onLost(network: Network) {
                        TransportNative.onNetworkChanged()
                    }
                },
            )
        }
    }
}
