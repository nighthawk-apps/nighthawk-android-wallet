package com.nighthawkapps.lib.android.sdk.mesh

/**
 * Wallet / chat path when lightwalletd or DarkIRC is unreachable.
 * Small RPCs ride BLE ctrl; UnifOMR never does — that is bulk join only.
 */
object MeshOfflineRelay {
    sealed class Plan {
        data object None : Plan()

        data class Ctrl(
            val dest: ByteArray,
            val method: String,
        ) : Plan()

        data class BulkJoin(
            val dest: ByteArray,
        ) : Plan()
    }

    fun dest(): ByteArray? = MeshNative.lastGatewayPeer() ?: MeshCoordinator.lastGatewayId

    fun plan(
        meshOn: Boolean,
        internetUnreachable: Boolean,
        needUnifOmr: Boolean,
        foreground: Boolean,
        method: String = "GetLightInfo",
    ): Plan {
        if (!meshOn || !internetUnreachable) return Plan.None
        val dest = dest() ?: return Plan.None
        if (needUnifOmr) {
            return if (foreground) Plan.BulkJoin(dest) else Plan.None
        }
        if (MeshLwdAllowlist.isForbidden(method)) return Plan.None
        if (!MeshLwdAllowlist.isAllowed("$method\n".toByteArray())) return Plan.None
        return Plan.Ctrl(dest, method)
    }

    @Suppress("UNUSED_PARAMETER")
    fun execute(plan: Plan): Boolean = false

    /** Internet lost: probe gateway with GetLightInfo. UnifOMR uses [requestBulkIfNeeded]. */
    fun onInternetUnreachable(
        meshOn: Boolean = NighthawkMeshService.isActive,
        foreground: Boolean = true,
    ): Boolean {
        val p =
            plan(
                meshOn = meshOn,
                internetUnreachable = true,
                needUnifOmr = false,
                foreground = foreground,
                method = "GetLightInfo",
            )
        return execute(p)
    }

    fun requestBulkIfNeeded(
        meshOn: Boolean = NighthawkMeshService.isActive,
        foreground: Boolean = true,
        fromBackgroundWorker: Boolean = false,
    ): Boolean {
        if (fromBackgroundWorker) return false
        if ((MeshCoordinator.lastGatewayCaps and 0x02) == 0) return false
        val p =
            plan(
                meshOn = meshOn,
                internetUnreachable = true,
                needUnifOmr = true,
                foreground = foreground,
            )
        return execute(p)
    }

    @Suppress("UNUSED_PARAMETER")
    fun publishChat(body: ByteArray): Boolean = false
}
