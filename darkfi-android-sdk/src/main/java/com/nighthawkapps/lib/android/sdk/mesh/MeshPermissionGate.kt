package com.nighthawkapps.lib.android.sdk.mesh

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Mesh is API 31+ so [Manifest.permission.BLUETOOTH_SCAN] can be
 * `neverForLocation`. Older Android would require location for BLE scan;
 * Nighthawk will not take that dependency.
 */
object MeshPermissionGate {
    const val MIN_SDK: Int = Build.VERSION_CODES.S

    fun isMeshSdkSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= MIN_SDK

    fun blePermissions(): Array<String> =
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
        )

    /** BLE plus notification grant for the `connectedDevice` FGS. */
    fun runtimeStartPermissions(): Array<String> {
        val ble = blePermissions()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ble + Manifest.permission.POST_NOTIFICATIONS
        } else {
            ble
        }
    }

    fun bulkWifiPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            emptyArray()
        }

    fun runtimeMeshPermissions(shareWifi: Boolean): Array<String> {
        val base = runtimeStartPermissions()
        return if (shareWifi) base + bulkWifiPermissions() else base
    }

    fun hasNearbyWifiPermission(context: Context): Boolean {
        val needed = bulkWifiPermissions()
        if (needed.isEmpty()) return true
        return needed.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasBlePermissions(context: Context): Boolean {
        if (!isMeshSdkSupported()) return false
        return blePermissions().all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun missingBlePermissions(context: Context): List<String> {
        if (!isMeshSdkSupported()) return blePermissions().toList()
        return blePermissions().filter { perm ->
            ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
        }
    }
}
