package com.nighthawkapps.lib.android.sdk.mesh

import android.Manifest
import android.os.Build
import com.nighthawkapps.lib.android.sdk.net.LocalNetworkPermission
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeshPermissionGateTest {
    @Test
    fun meshHiddenBelowAndroid12() {
        assertFalse(MeshPermissionGate.isMeshSdkSupported(Build.VERSION_CODES.Q))
        assertFalse(MeshPermissionGate.isMeshSdkSupported(Build.VERSION_CODES.R))
        assertTrue(MeshPermissionGate.isMeshSdkSupported(Build.VERSION_CODES.S))
        assertTrue(MeshPermissionGate.isMeshSdkSupported(33))
    }

    @Test
    fun blePermissionsAreNeverLocation() {
        val perms = MeshPermissionGate.blePermissions()
        assertTrue(perms.contains(Manifest.permission.BLUETOOTH_SCAN))
        assertTrue(perms.contains(Manifest.permission.BLUETOOTH_CONNECT))
        assertTrue(perms.contains(Manifest.permission.BLUETOOTH_ADVERTISE))
        assertFalse(perms.contains(Manifest.permission.ACCESS_FINE_LOCATION))
        assertFalse(perms.contains(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
        assertFalse(perms.contains(Manifest.permission.ACCESS_COARSE_LOCATION))
        val start = MeshPermissionGate.runtimeStartPermissions()
        assertFalse(start.contains(Manifest.permission.ACCESS_FINE_LOCATION))
    }

    @Test
    fun android17StartPermissionsIncludeLocalNetwork() {
        val start = MeshPermissionGate.runtimeMeshPermissions(shareWifi = false, sdkInt = 37)
        assertTrue(start.contains(LocalNetworkPermission.ACCESS_LOCAL_NETWORK))
        assertFalse(
            MeshPermissionGate.runtimeMeshPermissions(shareWifi = false, sdkInt = 36)
                .contains(LocalNetworkPermission.ACCESS_LOCAL_NETWORK),
        )
        val withWifi = MeshPermissionGate.runtimeMeshPermissions(shareWifi = true, sdkInt = 37)
        assertTrue(withWifi.contains(Manifest.permission.NEARBY_WIFI_DEVICES))
        assertTrue(withWifi.contains(LocalNetworkPermission.ACCESS_LOCAL_NETWORK))
    }

    @Test
    fun gattUuidsAreNotBitchat() {
        assertFalse(
            MeshGattConstants.SERVICE_UUID.toString().lowercase().startsWith("f47b5e2d"),
        )
    }

    @Test
    fun minSdkMatchesAndroid12() {
        assertEquals(Build.VERSION_CODES.S, MeshPermissionGate.MIN_SDK)
    }
}
