package com.nighthawkapps.lib.android.sdk.mesh

import com.nighthawkapps.lib.android.spackle.Twig
import com.sun.jna.Library
import com.sun.jna.Native
import java.util.concurrent.Executors

/**
 * Optional JNA surface for the mesh C ABI. Missing symbols (older
 * `libdarkfi_mobile_ffi.so`) must not crash the radio path.
 */
internal interface NhMeshLib : Library {
    fun nh_mesh_start(): Int

    fun nh_mesh_stop(): Int

    fun nh_mesh_set_gateway_eligible(optIn: Int): Int

    fun nh_mesh_set_os_power_state(
        foreground: Int,
        charging: Int,
        unmetered: Int,
    ): Int

    fun nh_mesh_ingest_link_bytes(
        ptr: ByteArray,
        len: Int,
    ): Int

    fun nh_mesh_pop_outbound(
        ptr: ByteArray,
        cap: Int,
    ): Int

    fun nh_mesh_pump_gateway(): Int

    fun nh_mesh_submit_lwd_ctrl(
        dest: ByteArray,
        method: String,
        body: ByteArray?,
        bodyLen: Int,
        corrOut: ByteArray,
    ): Int

    fun nh_mesh_pop_events_json(
        ptr: ByteArray,
        cap: Int,
    ): Int

    fun nh_mesh_wipe(): Int

    fun nh_mesh_submit_bulk_join(
        dest: ByteArray,
        corrOut: ByteArray,
    ): Int

    fun nh_mesh_submit_bulk_offer(
        dest: ByteArray,
        kind: Int,
        port: Int,
        ssid: String,
        psk: ByteArray?,
        pskLen: Int,
        sessionId: ByteArray,
    ): Int

    fun nh_mesh_set_bulk_tcp(
        host: String?,
        port: Int,
    ): Int

    fun nh_mesh_record_bulk_bytes(n: Long): Int

    fun nh_mesh_last_gateway_peer(
        ptr: ByteArray,
        cap: Int,
    ): Int

    fun nh_mesh_publish_dag(
        ptr: ByteArray,
        len: Int,
    ): Int

    fun nh_mesh_request_dag_sync(): Int
}

/** New C ABI symbols. Loaded separately so an older `.so` still binds [NhMeshLib]. */
internal interface NhMeshNeighborLib : Library {
    fun nh_mesh_neighbor_up(dest: ByteArray): Int

    fun nh_mesh_neighbor_down(dest: ByteArray): Int

    fun nh_mesh_peer_id(
        ptr: ByteArray,
        cap: Int,
    ): Int
}

object MeshNative {
    private val lock = Any()

    @Volatile
    private var tried = false

    @Volatile
    private var lib: NhMeshLib? = null

    @Volatile
    private var neighborTried = false

    @Volatile
    private var neighborLib: NhMeshNeighborLib? = null

    @Volatile
    var cacheEvicted: Boolean = false

    @Volatile
    var available: Boolean = false
        private set

    fun neighborsReady(): Boolean = neighborLib() != null

    fun drainEngineEvents() {
        val json = popEventsJson() ?: return
        if (json.contains("\"cache_full\"")) {
            cacheEvicted = true
        }
    }

    private val gatewayExec =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "nh-mesh-lwd").apply { isDaemon = true }
        }

    private fun lib(): NhMeshLib? {
        synchronized(lock) {
            if (tried) return lib
            tried = true
            lib =
                try {
                    Native.load("darkfi_mobile_ffi", NhMeshLib::class.java)
                } catch (_: Throwable) {
                    null
                }
            available = lib != null
            return lib
        }
    }

    private fun neighborLib(): NhMeshNeighborLib? {
        synchronized(lock) {
            if (neighborTried) return neighborLib
            neighborTried = true
            neighborLib =
                try {
                    Native.load("darkfi_mobile_ffi", NhMeshNeighborLib::class.java)
                } catch (_: Throwable) {
                    null
                }
            return neighborLib
        }
    }

    fun start() {
        runCatching { lib()?.nh_mesh_start() }
    }

    fun stop() {
        runCatching { lib()?.nh_mesh_stop() }
    }

    fun setGatewayEligible(on: Boolean) {
        runCatching { lib()?.nh_mesh_set_gateway_eligible(if (on) 1 else 0) }
    }

    fun setPower(
        foreground: Boolean,
        charging: Boolean,
        unmetered: Boolean,
    ) {
        runCatching {
            lib()?.nh_mesh_set_os_power_state(
                if (foreground) 1 else 0,
                if (charging) 1 else 0,
                if (unmetered) 1 else 0,
            )
        }
    }

    fun ingest(frame: ByteArray) {
        runCatching { lib()?.nh_mesh_ingest_link_bytes(frame, frame.size) }
    }

    fun flushOutbound(send: (ByteArray) -> Unit) {
        val native = lib() ?: return
        val buf = ByteArray(64 * 1024)
        repeat(32) {
            val n =
                try {
                    native.nh_mesh_pop_outbound(buf, buf.size)
                } catch (_: Throwable) {
                    return
                }
            if (n <= 0) return
            send(buf.copyOf(n))
        }
    }

    fun pumpGatewayAsync(flush: () -> Unit) {
        val native = lib() ?: return
        gatewayExec.execute {
            try {
                native.nh_mesh_pump_gateway()
            } catch (_: Throwable) {
                Twig.warn { "mesh: gateway pump skipped" }
            }
            flush()
        }
    }

    fun submitLwdCtrl(
        dest: ByteArray,
        method: String,
        body: ByteArray = ByteArray(0),
    ): Boolean {
        if (dest.size != 8) return false
        val corr = ByteArray(16)
        val rc =
            runCatching {
                lib()?.nh_mesh_submit_lwd_ctrl(dest, method, body, body.size, corr)
            }.getOrNull() ?: return false
        return rc == 0
    }

    fun popEventsJson(): String? {
        val native = lib() ?: return null
        val buf = ByteArray(64 * 1024)
        val n =
            try {
                native.nh_mesh_pop_events_json(buf, buf.size)
            } catch (_: Throwable) {
                return null
            }
        if (n <= 0) return null
        return String(buf, 0, n, Charsets.UTF_8)
    }

    fun submitBulkJoin(dest: ByteArray): Boolean {
        if (dest.size != 8) return false
        val corr = ByteArray(16)
        val rc =
            runCatching { lib()?.nh_mesh_submit_bulk_join(dest, corr) }.getOrNull() ?: return false
        return rc == 0
    }

    fun submitBulkOffer(
        dest: ByteArray,
        kind: Int,
        port: Int,
        ssid: String,
        psk: ByteArray,
        sessionId: ByteArray = ByteArray(16),
    ): Boolean {
        if (dest.size != 8) return false
        val rc =
            runCatching {
                lib()?.nh_mesh_submit_bulk_offer(dest, kind, port, ssid, psk, psk.size, sessionId)
            }.getOrNull() ?: return false
        return rc == 0
    }

    fun setBulkTcp(
        host: String?,
        port: Int,
    ) {
        runCatching { lib()?.nh_mesh_set_bulk_tcp(host, port) }
    }

    fun recordBulkBytes(n: Long): Boolean {
        val rc = runCatching { lib()?.nh_mesh_record_bulk_bytes(n) }.getOrNull() ?: return false
        return rc == 1
    }

    fun wipe() {
        runCatching { lib()?.nh_mesh_wipe() }
        setBulkTcp(null, 0)
    }

    fun lastGatewayPeer(): ByteArray? {
        val native = lib() ?: return null
        val buf = ByteArray(8)
        val n =
            try {
                native.nh_mesh_last_gateway_peer(buf, buf.size)
            } catch (_: Throwable) {
                return null
            }
        if (n != 8) return null
        return buf
    }

    fun publishDag(body: ByteArray): Boolean {
        val rc = runCatching { lib()?.nh_mesh_publish_dag(body, body.size) }.getOrNull() ?: return false
        return rc == 0
    }

    fun requestDagSync() {
        runCatching { lib()?.nh_mesh_request_dag_sync() }
    }

    fun neighborUp(dest: ByteArray) {
        if (dest.size != 8) return
        runCatching { neighborLib()?.nh_mesh_neighbor_up(dest) }
    }

    fun neighborDown(dest: ByteArray) {
        if (dest.size != 8) return
        runCatching { neighborLib()?.nh_mesh_neighbor_down(dest) }
    }

    fun peerId(): ByteArray? {
        val native = neighborLib() ?: return null
        val buf = ByteArray(8)
        val n =
            try {
                native.nh_mesh_peer_id(buf, buf.size)
            } catch (_: Throwable) {
                return null
            }
        if (n != 8) return null
        return buf
    }
}
