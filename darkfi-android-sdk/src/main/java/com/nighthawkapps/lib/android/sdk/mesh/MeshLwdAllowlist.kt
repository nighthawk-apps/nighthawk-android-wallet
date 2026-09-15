package com.nighthawkapps.lib.android.sdk.mesh

/**
 * BLE must never carry UnifOMR bulk or compact-block dumps.
 * Mirrors `darkfi-mobile-ffi` `mesh/allowlist.rs`.
 */
object MeshLwdAllowlist {
    const val MAX_CTRL_BYTES: Int = 32 * 1024

    val ALLOWED: Set<String> =
        setOf(
            "GetLightInfo",
            "GetOmrCapabilities",
            "SendTransaction",
            "RegisterCluePublicKey",
            "GetCluePublicKey",
        )

    val FORBIDDEN: Set<String> =
        setOf(
            "GetUnifOmrDigest",
            "FetchPirBatch",
            "GetBlockRange",
            "GetNoteCommitments",
            "GetNullifiers",
            "GetCompactBlocks",
        )

    fun methodLeaf(payload: ByteArray): String? {
        if (payload.isEmpty() || payload.size > MAX_CTRL_BYTES) return null
        val nl = payload.indexOf('\n'.code.toByte()).let { if (it < 0) payload.size else it }
        return payload.decodeToString(0, nl)
    }

    fun isForbidden(method: String): Boolean =
        FORBIDDEN.any { it.equals(method, ignoreCase = true) }

    fun isAllowed(payload: ByteArray): Boolean {
        val method = methodLeaf(payload) ?: return false
        if (FORBIDDEN.any { it.equals(method, ignoreCase = true) }) return false
        return ALLOWED.any { it.equals(method, ignoreCase = true) }
    }
}
