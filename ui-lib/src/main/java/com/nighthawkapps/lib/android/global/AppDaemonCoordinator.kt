package com.nighthawkapps.lib.android.global

import android.content.Context
import com.nighthawkapps.lib.android.sdk.daemon.DarkfiDaemonLifecycleCoordinator

object AppDaemonCoordinator {
    fun install(
        context: Context,
        autoStart: Boolean = true,
    ) {
        DarkfiDaemonLifecycleCoordinator.install(context, autoStart = autoStart) {
            AppWalletCoordinator.get(context)
        }
    }

    fun get(): DarkfiDaemonLifecycleCoordinator = DarkfiDaemonLifecycleCoordinator.get()
}
