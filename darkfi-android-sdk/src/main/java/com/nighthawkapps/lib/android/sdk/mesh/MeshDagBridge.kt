package com.nighthawkapps.lib.android.sdk.mesh

/**
 * Native-side DAG admit before EventGraph ingest. Size-caps BLE
 * `NH_DAG_EVENT` bodies so a radio path cannot become a compact-block
 * dump. Structural / RLN checks stay in `EventGraph::ingest_mesh_event`.
 */
enum class MeshDagAdmit {
    Ignored,
    Admitted,
    RejectedOversized,
    RejectedForbiddenLwd,
}

class MeshDagBridge(
    val maxBlobBytes: Int = MAX_MESH_EVENT_BLOB,
) {
    private val admitted = ArrayDeque<ByteArray>()

    val admittedCount: Int
        @Synchronized get() = admitted.size

    @Synchronized
    fun consider(pkt: MeshWirePacket): MeshDagAdmit = gate(pkt) ?: MeshDagAdmit.Ignored

    private fun gate(pkt: MeshWirePacket): MeshDagAdmit? {
        if (pkt.type == MeshWireCodec.NH_LWD_CTRL) return MeshDagAdmit.RejectedForbiddenLwd
        return dagOrNoise(pkt)
    }

    private fun dagOrNoise(pkt: MeshWirePacket): MeshDagAdmit? {
        if (pkt.type == MeshWireCodec.NH_DAG_EVENT || pkt.type == MeshWireCodec.NH_DAG_SYNC) {
            return MeshDagAdmit.Ignored
        }
        return noiseIfOversized(pkt)
    }

    private fun noiseIfOversized(pkt: MeshWirePacket): MeshDagAdmit? {
        if (pkt.type == MeshWireCodec.NH_NOISE_ENC && pkt.payload.size > maxBlobBytes) {
            return MeshDagAdmit.RejectedOversized
        }
        return null
    }

    @Synchronized
    fun popAdmitted(): List<ByteArray> {
        val out = admitted.toList()
        admitted.clear()
        return out
    }

    companion object {
        const val MAX_MESH_EVENT_BLOB: Int = 1024 * 1024 - 512
    }
}
