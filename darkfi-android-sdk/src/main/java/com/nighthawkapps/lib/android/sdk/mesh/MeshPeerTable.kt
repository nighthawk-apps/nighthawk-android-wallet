package com.nighthawkapps.lib.android.sdk.mesh

/**
 * Connection budget for dual-GATT. Keys are opaque radio ids
 * (never log them — they are Bluetooth addresses).
 */
class MeshPeerTable(
    val maxPeers: Int = MeshGattConstants.MAX_CONNECTIONS,
) {
    private data class Slot(
        val id: String,
        val admittedAtMs: Long,
    )

    private val order = ArrayList<Slot>()

    val size: Int get() = order.size

    fun contains(id: String): Boolean = order.any { it.id == id }

    /** @return evicted id if the table was full, or null. */
    fun admit(
        id: String,
        nowMs: Long,
    ): String? {
        if (contains(id)) return null
        var evicted: String? = null
        if (order.size >= maxPeers) {
            evicted = order.removeAt(0).id
        }
        order.add(Slot(id, nowMs))
        return evicted
    }

    fun forget(id: String) {
        order.removeAll { it.id == id }
    }

    fun clear() {
        order.clear()
    }
}
