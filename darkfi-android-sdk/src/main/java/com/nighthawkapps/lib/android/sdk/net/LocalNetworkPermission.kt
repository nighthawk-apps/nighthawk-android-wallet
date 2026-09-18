package com.nighthawkapps.lib.android.sdk.net

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Android 17 (API 37) blocks LAN sockets unless [ACCESS_LOCAL_NETWORK] is granted.
 * Loopback and public internet are unaffected. Same [NEARBY_DEVICES] group as BLE,
 * so a prior nearby grant is not re-prompted.
 */
object LocalNetworkPermission {
    /** API 37 — [Build.VERSION_CODES.CINNAMON_BUN] when the stub is present. */
    const val ANDROID_17: Int = 37

    const val ACCESS_LOCAL_NETWORK: String = "android.permission.ACCESS_LOCAL_NETWORK"

    fun isRequired(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= ANDROID_17

    fun runtimePermissions(sdkInt: Int = Build.VERSION.SDK_INT): Array<String> =
        if (isRequired(sdkInt)) arrayOf(ACCESS_LOCAL_NETWORK) else emptyArray()

    fun hasGranted(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
        val needed = runtimePermissions(sdkInt)
        if (needed.isEmpty()) return true
        return needed.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }
}
