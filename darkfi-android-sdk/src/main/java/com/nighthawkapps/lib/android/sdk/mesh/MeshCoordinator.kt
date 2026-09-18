package com.nighthawkapps.lib.android.sdk.mesh

import android.app.Activity
import android.content.Context
import android.os.Build
import com.nighthawkapps.lib.android.spackle.Twig

/**
 * Foreground-only FGS start from UI. Boot / package-replace is a documented
 * Android exemption and goes through [startFromBootIfEnabled].
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

    @Volatile
    private var app: Context? = null

    fun bind(context: Context) {
        val appCtx = context.applicationContext
        app = appCtx
        alwaysOn = MeshPersist.alwaysOn(appCtx)
    }

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
        app?.let { MeshPersist.setAlwaysOn(it, on) }
        NighthawkMeshService.notifyPowerChanged()
    }

    fun setLwdSpliceTarget(
        host: String?,
        port: Int,
    ) {
        lwdSpliceHost = host
        lwdSplicePort = port
    }

    fun stickyRestart(): Boolean = alwaysOn

    fun enableFromForeground(context: Context): Boolean {
        bind(context)
        val activity = context.findActivity()
        if (activity == null) {
            Twig.warn { "mesh: enable requested without an Activity" }
            return false
        }
        if (!canShowMeshToggle()) return false
        if (!MeshPermissionGate.hasBlePermissions(activity)) {
            Twig.warn { "mesh: enable requested without BLE runtime grants" }
            return false
        }
        MeshPersist.setEnabled(activity, true)
        MeshNative.setGatewayEligible(gatewayOptIn)
        return NighthawkMeshService.startFromForeground(activity)
    }

    fun disable(context: Context) {
        bind(context)
        MeshPersist.setEnabled(context, false)
        MeshNative.setGatewayEligible(false)
        NighthawkMeshService.stop(context)
    }

    fun restoreOnResume(activity: Activity): Boolean {
        bind(activity)
        if (!canShowMeshToggle()) return false
        if (!MeshPersist.isEnabled(activity)) return false
        if (!MeshPermissionGate.hasBlePermissions(activity)) {
            Twig.warn { "mesh: resume skipped — BLE permission missing" }
            return false
        }
        MeshNative.setGatewayEligible(false)
        return NighthawkMeshService.startFromForeground(activity)
    }

    fun startFromBootIfEnabled(context: Context): Boolean {
        bind(context)
        if (!canShowMeshToggle()) return false
        if (!MeshPersist.isEnabled(context) || !alwaysOn) return false
        if (!MeshPermissionGate.hasBlePermissions(context)) return false
        MeshNative.setGatewayEligible(false)
        return NighthawkMeshService.startFromBootExemption(context)
    }
}
