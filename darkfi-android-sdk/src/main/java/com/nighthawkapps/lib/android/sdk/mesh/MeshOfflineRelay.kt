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
        val dest = MeshOfflinePlans.reachableDest(meshOn, internetUnreachable) ?: return Plan.None
        return MeshOfflinePlans.forDest(dest, needUnifOmr, foreground, method)
    }

    fun execute(plan: Plan): Boolean =
        when (plan) {
            is Plan.None, is Plan.Ctrl, is Plan.BulkJoin -> false
        }

    /** Internet lost: probe gateway with GetLightInfo. UnifOMR uses [requestBulkIfNeeded]. */
    fun onInternetUnreachable(
        meshOn: Boolean = NighthawkMeshService.isActive,
        foreground: Boolean = true,
    ): Boolean {
        val planned =
            plan(
                meshOn = meshOn,
                internetUnreachable = true,
                needUnifOmr = false,
                foreground = foreground,
                method = "GetLightInfo",
            )
        return execute(planned)
    }

    fun requestBulkIfNeeded(
        meshOn: Boolean = NighthawkMeshService.isActive,
        foreground: Boolean = true,
        fromBackgroundWorker: Boolean = false,
    ): Boolean {
        if (!MeshOfflinePlans.bulkAllowed(fromBackgroundWorker)) return false
        val planned =
            plan(
                meshOn = meshOn,
                internetUnreachable = true,
                needUnifOmr = true,
                foreground = foreground,
            )
        return execute(planned)
    }

    fun publishChat(body: ByteArray): Boolean =
        when {
            body.isEmpty() -> false
            else -> false
        }
}

private const val GATEWAY_CAP_BULK: Int = 0x02

private object MeshOfflinePlans {
    fun reachableDest(
        meshOn: Boolean,
        internetUnreachable: Boolean,
    ): ByteArray? {
        if (!meshOn || !internetUnreachable) return null
        return MeshOfflineRelay.dest()
    }

    fun forDest(
        dest: ByteArray,
        needUnifOmr: Boolean,
        foreground: Boolean,
        method: String,
    ): MeshOfflineRelay.Plan {
        if (needUnifOmr) return bulk(dest, foreground)
        return ctrl(dest, method)
    }

    fun bulkAllowed(fromBackgroundWorker: Boolean): Boolean {
        if (fromBackgroundWorker) return false
        return (MeshCoordinator.lastGatewayCaps and GATEWAY_CAP_BULK) != 0
    }

    private fun bulk(
        dest: ByteArray,
        foreground: Boolean,
    ): MeshOfflineRelay.Plan =
        if (foreground) MeshOfflineRelay.Plan.BulkJoin(dest) else MeshOfflineRelay.Plan.None

    private fun ctrl(
        dest: ByteArray,
        method: String,
    ): MeshOfflineRelay.Plan {
        if (methodRejected(method)) return MeshOfflineRelay.Plan.None
        return MeshOfflineRelay.Plan.Ctrl(dest, method)
    }

    private fun methodRejected(method: String): Boolean {
        if (MeshLwdAllowlist.isForbidden(method)) return true
        return !MeshLwdAllowlist.isAllowed("$method\n".toByteArray())
    }
}
