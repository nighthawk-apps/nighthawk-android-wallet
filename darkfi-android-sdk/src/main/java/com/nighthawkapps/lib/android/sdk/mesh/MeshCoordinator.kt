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
    var lwdSplicePort: Int = DEFAULT_LWD_SPLICE_PORT
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

    fun canShowMeshToggle(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = MeshPermissionGate.isMeshSdkSupported(sdkInt)

    fun noteGatewayAnnounce(
        id: ByteArray,
        caps: Int,
    ) {
        if (id.size != MeshWireCodec.SENDER_LEN) return
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

    fun disable(context: Context) {
        bind(context)
        MeshPersist.setEnabled(context, false)
        MeshNative.setGatewayEligible(false)
        NighthawkMeshService.stop(context)
    }

    /** Default lightwalletd TLS port when the UI has not set a splice target. */
    private const val DEFAULT_LWD_SPLICE_PORT: Int = 443
}

fun MeshCoordinator.enableFromForeground(context: Context): Boolean {
    bind(context)
    val activity = MeshStartGate.activityForEnable(context) ?: return false
    return MeshStartGate.enableOn(activity)
}

fun MeshCoordinator.restoreOnResume(activity: Activity): Boolean {
    bind(activity)
    if (!MeshStartGate.resumeAllowed(activity)) return false
    MeshNative.setGatewayEligible(false)
    return NighthawkMeshService.startFromForeground(activity)
}

fun MeshCoordinator.startFromBootIfEnabled(context: Context): Boolean {
    bind(context)
    if (!MeshStartGate.bootStartAllowed(context)) return false
    MeshNative.setGatewayEligible(false)
    return NighthawkMeshService.startFromBootExemption(context)
}

private object MeshStartGate {
    fun activityForEnable(context: Context): Activity? {
        val activity = context.findActivity()
        if (activity != null) return activity
        Twig.warn { "mesh: enable requested without an Activity" }
        return null
    }

    fun enableOn(activity: Activity): Boolean {
        if (!meshToggleGranted(activity)) return false
        MeshPersist.setEnabled(activity, true)
        MeshNative.setGatewayEligible(MeshCoordinator.gatewayOptIn)
        return NighthawkMeshService.startFromForeground(activity)
    }

    fun meshToggleGranted(activity: Activity): Boolean {
        if (!MeshCoordinator.canShowMeshToggle()) return false
        return bleGranted(activity, "mesh: enable requested without BLE runtime grants")
    }

    fun resumeAllowed(activity: Activity): Boolean {
        if (!toggleAndPersisted(activity)) return false
        return bleGranted(activity, "mesh: resume skipped — BLE permission missing")
    }

    fun bootStartAllowed(context: Context): Boolean {
        if (!MeshCoordinator.canShowMeshToggle() || !bootPrefs(context)) return false
        return MeshPermissionGate.hasBlePermissions(context)
    }

    private fun toggleAndPersisted(activity: Activity): Boolean = MeshCoordinator.canShowMeshToggle() && MeshPersist.isEnabled(activity)

    private fun bootPrefs(context: Context): Boolean = MeshPersist.isEnabled(context) && MeshCoordinator.alwaysOn

    private fun bleGranted(
        activity: Activity,
        denialMessage: String,
    ): Boolean {
        if (MeshPermissionGate.hasBlePermissions(activity)) return true
        Twig.warn { denialMessage }
        return false
    }
}
