package com.nighthawkapps.lib.android.sdk.mesh

fun MeshNative.flushOutbound(send: (ByteArray) -> Unit) {
    val native = lib() ?: return
    MeshNativeCalls.popOutbound(native, send)
}

fun MeshNative.popEventsJson(): String? {
    val native = lib() ?: return null
    return MeshNativeCalls.eventsJson(native)
}

fun MeshNative.submitLwdCtrl(
    dest: ByteArray,
    method: String,
    body: ByteArray = ByteArray(0),
): Boolean {
    if (dest.size != MeshWireCodec.SENDER_LEN) return false
    return MeshNativeCalls.ctrlAccepted(this, dest, method, body)
}

fun MeshNative.submitBulkJoin(dest: ByteArray): Boolean {
    if (dest.size != MeshWireCodec.SENDER_LEN) return false
    return MeshNativeCalls.bulkJoinAccepted(this, dest)
}

// Arity matches the C symbol nh_mesh_submit_bulk_offer.
@Suppress("LongParameterList")
fun MeshNative.submitBulkOffer(
    dest: ByteArray,
    kind: Int,
    port: Int,
    ssid: String,
    psk: ByteArray,
    sessionId: ByteArray = ByteArray(MeshNative.MESH_CORRELATION_BYTES),
): Boolean {
    if (dest.size != MeshWireCodec.SENDER_LEN) return false
    return MeshNativeCalls.bulkOfferAccepted(
        this,
        BulkOffer(dest, kind, port, ssid, psk, sessionId),
    )
}

fun MeshNative.setBulkTcp(
    host: String?,
    port: Int,
) {
    runCatching { lib()?.nh_mesh_set_bulk_tcp(host, port) }
}

fun MeshNative.recordBulkBytes(n: Long): Boolean {
    val rc = runCatching { lib()?.nh_mesh_record_bulk_bytes(n) }.getOrNull() ?: return false
    return rc == MESH_RECORD_ACCEPTED
}

fun MeshNative.lastGatewayPeer(): ByteArray? {
    val native = lib() ?: return null
    return MeshNativeCalls.gatewayPeer(native)
}

fun MeshNative.publishDag(body: ByteArray): Boolean {
    val rc = runCatching { lib()?.nh_mesh_publish_dag(body, body.size) }.getOrNull() ?: return false
    return rc == 0
}

fun MeshNative.requestDagSync() {
    runCatching { lib()?.nh_mesh_request_dag_sync() }
}

private const val MESH_RECORD_ACCEPTED: Int = 1

private class BulkOffer(
    val dest: ByteArray,
    val kind: Int,
    val port: Int,
    val ssid: String,
    val psk: ByteArray,
    val sessionId: ByteArray,
)

private object MeshNativeCalls {
    fun popOutbound(
        native: NhMeshLib,
        send: (ByteArray) -> Unit,
    ) {
        val buf = ByteArray(MeshNative.MESH_IO_BUFFER_BYTES)
        repeat(MeshNative.MESH_OUTBOUND_POP_LIMIT) {
            val n = popOne(native, buf) ?: return
            if (n <= 0) return
            send(buf.copyOf(n))
        }
    }

    fun eventsJson(native: NhMeshLib): String? {
        val buf = ByteArray(MeshNative.MESH_IO_BUFFER_BYTES)
        val n = popEvents(native, buf) ?: return null
        return eventsText(buf, n)
    }

    fun ctrlAccepted(
        mesh: MeshNative,
        dest: ByteArray,
        method: String,
        body: ByteArray,
    ): Boolean {
        val corr = ByteArray(MeshNative.MESH_CORRELATION_BYTES)
        val rc =
            runCatching {
                mesh.lib()?.nh_mesh_submit_lwd_ctrl(dest, method, body, body.size, corr)
            }.getOrNull() ?: return false
        return rc == 0
    }

    fun bulkJoinAccepted(
        mesh: MeshNative,
        dest: ByteArray,
    ): Boolean {
        val corr = ByteArray(MeshNative.MESH_CORRELATION_BYTES)
        val rc = runCatching { mesh.lib()?.nh_mesh_submit_bulk_join(dest, corr) }.getOrNull() ?: return false
        return rc == 0
    }

    fun bulkOfferAccepted(
        mesh: MeshNative,
        offer: BulkOffer,
    ): Boolean {
        val rc =
            runCatching {
                mesh.lib()?.nh_mesh_submit_bulk_offer(
                    offer.dest,
                    offer.kind,
                    offer.port,
                    offer.ssid,
                    offer.psk,
                    offer.psk.size,
                    offer.sessionId,
                )
            }.getOrNull() ?: return false
        return rc == 0
    }

    fun gatewayPeer(native: NhMeshLib): ByteArray? {
        val buf = ByteArray(MeshWireCodec.SENDER_LEN)
        val n = readGateway(native, buf) ?: return null
        return if (n == MeshWireCodec.SENDER_LEN) buf else null
    }

    private fun popOne(
        native: NhMeshLib,
        buf: ByteArray,
    ): Int? =
        try {
            native.nh_mesh_pop_outbound(buf, buf.size)
        } catch (_: Throwable) {
            null
        }

    private fun popEvents(
        native: NhMeshLib,
        buf: ByteArray,
    ): Int? =
        try {
            native.nh_mesh_pop_events_json(buf, buf.size)
        } catch (_: Throwable) {
            null
        }

    private fun eventsText(
        buf: ByteArray,
        n: Int,
    ): String? {
        if (n <= 0) return null
        return String(buf, 0, n, Charsets.UTF_8)
    }

    private fun readGateway(
        native: NhMeshLib,
        buf: ByteArray,
    ): Int? =
        try {
            native.nh_mesh_last_gateway_peer(buf, buf.size)
        } catch (_: Throwable) {
            null
        }
}
