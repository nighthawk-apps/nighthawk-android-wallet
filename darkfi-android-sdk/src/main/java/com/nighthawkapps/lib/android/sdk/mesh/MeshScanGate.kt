package com.nighthawkapps.lib.android.sdk.mesh

/**
 * A scan callback often fires on the advertising packet before the scan
 * response that carries the 8-byte mesh id. Opening GATT on that first
 * callback pins the peer, and the later result is ignored, so neither
 * phone ever calls neighbor-up.
 */
internal object MeshScanGate {
    fun meshIdFromAdvertisement(data: ByteArray?): ByteArray? {
        val valid = data?.takeIf { it.size == MeshWireCodec.SENDER_LEN }
        return valid?.copyOf()
    }

    fun shouldOpen(
        alreadyLinked: Boolean,
        meshId: ByteArray?,
    ): Boolean = meshId != null && meshId.size == MeshWireCodec.SENDER_LEN && !alreadyLinked

    fun shouldAttach(
        alreadyLinked: Boolean,
        meshId: ByteArray?,
    ): Boolean = meshId != null && meshId.size == MeshWireCodec.SENDER_LEN && alreadyLinked
}
