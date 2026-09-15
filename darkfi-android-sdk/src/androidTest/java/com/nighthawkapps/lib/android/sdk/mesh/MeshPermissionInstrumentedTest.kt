package com.nighthawkapps.lib.android.sdk.mesh

import android.content.pm.ServiceInfo
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MeshPermissionInstrumentedTest {
    @Test
    fun sdkGateMatchesDevice() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            MeshPermissionGate.isMeshSdkSupported(),
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            assertFalse(MeshPermissionGate.hasBlePermissions(ctx))
        }
    }

    @Test
    fun serviceClassIsResolvable() {
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
    }
}
