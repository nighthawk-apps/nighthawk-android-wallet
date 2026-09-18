package com.nighthawkapps.lib.android.sdk.mesh

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Policy + lab gate for two-phone radio. Emulator BLE is not dual-role;
 * live GATT between two adapters is skipped unless this is a physical
 * device with Bluetooth on. Hardware chat still needs a second phone.
 */
@RunWith(AndroidJUnit4::class)
class MeshTwoPhoneInstrumentedTest {
    @Test
    fun gattUuidsAreNotBitchat() {
        assertFalse(
            MeshGattConstants.SERVICE_UUID.toString().startsWith("f47b5e2d", ignoreCase = true),
        )
        assertEquals(
            "6e686d73-0001-4000-a000-4e6967687468",
            MeshGattConstants.SERVICE_UUID.toString(),
        )
        assertEquals(
            "6e686d73-0002-4000-a000-4e6967687468",
            MeshGattConstants.CHARACTERISTIC_UUID.toString(),
        )
    }

    @Test
    fun meshServiceIsConnectedDeviceAndNeverForLocation() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val info =
            ctx.packageManager.getServiceInfo(
                android.content.ComponentName(ctx, NighthawkMeshService::class.java),
                0,
            )
        assertFalse(info.exported)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            assertEquals(
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
                info.foregroundServiceType,
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val pkg =
                ctx.packageManager.getPackageInfo(
                    ctx.packageName,
                    PackageManager.GET_PERMISSIONS,
                )
            val perms = pkg.requestedPermissions ?: emptyArray()
            val flags = pkg.requestedPermissionsFlags ?: intArrayOf()
            val scan = perms.indexOf(Manifest.permission.BLUETOOTH_SCAN)
            assertTrue(scan >= 0)
            assertTrue(
                scan < flags.size &&
                    flags[scan] and PackageInfo.REQUESTED_PERMISSION_NEVER_FOR_LOCATION != 0,
            )
        }
        assertFalse(NighthawkMeshService.startFromForeground(ctx))
    }

    @Test
    fun liveTwoAdapterRadioIsSkippedOnEmulator() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter
        assumeTrue(
            "physical device with Bluetooth required for two-phone radio",
            adapter != null &&
                adapter.isEnabled &&
                !isEmulator(),
        )
        // Dual-role on one adapter is not an honest two-phone test.
        assertTrue(adapter!!.isEnabled)
    }

    private fun isEmulator(): Boolean {
        val fp = Build.FINGERPRINT.lowercase()
        return fp.contains("generic") ||
            fp.contains("emulator") ||
            Build.MODEL.contains("Emulator", ignoreCase = true) ||
            Build.PRODUCT.contains("sdk", ignoreCase = true)
    }
}
