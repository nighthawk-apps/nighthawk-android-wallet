package com.nighthawkapps.lib.android.sdk.mesh

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restarts the connectedDevice FGS after reboot when the user left mesh
 * + always-on enabled. Android 12+ allows FGS start from BOOT_COMPLETED.
 */
class MeshBootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        MeshCoordinator.startFromBootIfEnabled(context.applicationContext)
    }
}
