package com.nighthawkapps.lib.android.sdk.mesh

fun MeshNative.wipe() {
    runCatching { lib()?.nh_mesh_wipe() }
    setBulkTcp(null, 0)
}

fun MeshNative.neighborUp(dest: ByteArray) {
    if (dest.size != MeshWireCodec.SENDER_LEN) return
    runCatching { neighborLib()?.nh_mesh_neighbor_up(dest) }
}

fun MeshNative.neighborDown(dest: ByteArray) {
    if (dest.size != MeshWireCodec.SENDER_LEN) return
    runCatching { neighborLib()?.nh_mesh_neighbor_down(dest) }
}

fun MeshNative.peerId(): ByteArray? {
    val native = neighborLib() ?: return null
    return MeshPeerCalls.idBytes(native)
}

private object MeshPeerCalls {
    fun idBytes(native: NhMeshNeighborLib): ByteArray? {
        val buf = ByteArray(MeshWireCodec.SENDER_LEN)
        val n = readId(native, buf) ?: return null
        return if (n == MeshWireCodec.SENDER_LEN) buf else null
    }

    private fun readId(
        native: NhMeshNeighborLib,
        buf: ByteArray,
    ): Int? =
        try {
            native.nh_mesh_peer_id(buf, buf.size)
        } catch (_: Throwable) {
            null
        }
}
