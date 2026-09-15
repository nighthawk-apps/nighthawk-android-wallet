package com.nighthawkapps.lib.android.sdk.mesh

import android.app.Activity
import android.content.Context
import android.os.Build
import com.nighthawkapps.lib.android.spackle.Twig

/**
 * Foreground-only entry point. Android 12+ rejects FGS starts from a
 * background [android.content.BroadcastReceiver].
 */
object MeshCoordinator {
    @Volatile
    var gatewayOptIn: Boolean = false
        private set

    @Volatile
    var alwaysOn: Boolean = true
        private set

    @Volatile
    var lwdSpliceHost: String? = null
        private set

    @Volatile
    var lwdSplicePort: Int = 443
        private set

    @Volatile
    var lastGatewayCaps: Int = 0
        private set

    @Volatile
    var lastGatewayId: ByteArray? = null
        private set

    fun canShowMeshToggle(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
        MeshPermissionGate.isMeshSdkSupported(sdkInt)

    fun noteGatewayAnnounce(
        id: ByteArray,
        caps: Int,
    ) {
        if (id.size != 8) return
        lastGatewayId = id.copyOf()
        lastGatewayCaps = caps
    }

    fun clearGatewayHint() {
        lastGatewayId = null
        lastGatewayCaps = 0
    }

    fun setGatewayOptIn(on: Boolean) {
        if (on) {
            Twig.debug { "mesh: share-internet remains disabled this pass" }
        }
        gatewayOptIn = false
        MeshNative.setGatewayEligible(false)
    }

    fun setAlwaysOn(on: Boolean) {
        alwaysOn = on
    }

    fun setLwdSpliceTarget(
        host: String?,
        port: Int,
    ) {
        lwdSpliceHost = host
        lwdSplicePort = port
    }

    fun enableFromForeground(activity: Activity): Boolean {
        if (!canShowMeshToggle()) return false
        if (!MeshPermissionGate.hasBlePermissions(activity)) {
            Twig.warn { "mesh: enable requested without BLE runtime grants" }
            return false
        }
        MeshNative.setGatewayEligible(gatewayOptIn)
        return NighthawkMeshService.startFromForeground(activity)
    }

    fun disable(context: Context) {
        MeshNative.setGatewayEligible(false)
        NighthawkMeshService.stop(context)
    }
}
