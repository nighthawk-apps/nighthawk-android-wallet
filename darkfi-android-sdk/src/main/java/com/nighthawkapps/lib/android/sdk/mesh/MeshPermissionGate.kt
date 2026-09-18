package com.nighthawkapps.lib.android.sdk.mesh

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.nighthawkapps.lib.android.sdk.net.LocalNetworkPermission

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
    fun runtimeStartPermissions(sdkInt: Int = Build.VERSION.SDK_INT): Array<String> {
        val ble = blePermissions()
        val withNotif =
            if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
                ble + Manifest.permission.POST_NOTIFICATIONS
            } else {
                ble
            }
        return withNotif + LocalNetworkPermission.runtimePermissions(sdkInt)
    }

    fun bulkWifiPermissions(sdkInt: Int = Build.VERSION.SDK_INT): Array<String> =
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            emptyArray()
        }

    fun runtimeMeshPermissions(shareWifi: Boolean, sdkInt: Int = Build.VERSION.SDK_INT): Array<String> {
        val base = runtimeStartPermissions(sdkInt)
        return if (shareWifi) base + bulkWifiPermissions(sdkInt) else base
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

    fun isBluetoothAdapterEnabled(context: Context): Boolean {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        return adapter?.isEnabled == true
    }
}
