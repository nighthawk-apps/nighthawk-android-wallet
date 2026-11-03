package com.nighthawkapps.lib.android.sdk.mesh

import com.nighthawkapps.lib.android.spackle.Twig
import com.sun.jna.Library
import com.sun.jna.Native
import java.util.concurrent.Executors

/**
 * Optional JNA surface for the mesh C ABI. Missing symbols (older
 * `libdarkfi_mobile_ffi.so`) must not crash the radio path.
 */
// Names match the C ABI of libdarkfi_mobile_ffi.
@Suppress("FunctionNaming", "TooManyFunctions")
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

    // Arity matches the C symbol nh_mesh_submit_bulk_offer.
    @Suppress("LongParameterList")
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
// Names match the C ABI of libdarkfi_mobile_ffi.
@Suppress("FunctionNaming")
internal interface NhMeshNeighborLib : Library {
    fun nh_mesh_neighbor_up(dest: ByteArray): Int

    fun nh_mesh_neighbor_down(dest: ByteArray): Int

    fun nh_mesh_peer_id(
        ptr: ByteArray,
        cap: Int,
    ): Int
}

object MeshNative {
    internal const val MESH_CORRELATION_BYTES: Int = 16
    internal const val MESH_IO_BUFFER_BYTES: Int = 64 * 1024
    internal const val MESH_OUTBOUND_POP_LIMIT: Int = 32

    private val lock = Any()

    @Volatile
    private var tried = false

    @Volatile
    private var libHandle: NhMeshLib? = null

    @Volatile
    private var neighborTried = false

    @Volatile
    private var neighborHandle: NhMeshNeighborLib? = null

    @Volatile
    var cacheEvicted: Boolean = false

    @Volatile
    var available: Boolean = false
        private set

    private val gatewayExec =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "nh-mesh-lwd").apply { isDaemon = true }
        }

    fun neighborsReady(): Boolean = neighborLib() != null

    fun drainEngineEvents() {
        val json = popEventsJson() ?: return
        if (json.contains("\"cache_full\"")) {
            cacheEvicted = true
        }
    }

    internal fun lib(): NhMeshLib? {
        synchronized(lock) {
            if (tried) return libHandle
            tried = true
            libHandle =
                try {
                    Native.load("darkfi_mobile_ffi", NhMeshLib::class.java)
                } catch (_: Throwable) {
                    null
                }
            available = libHandle != null
            return libHandle
        }
    }

    internal fun neighborLib(): NhMeshNeighborLib? {
        synchronized(lock) {
            if (neighborTried) return neighborHandle
            neighborTried = true
            neighborHandle =
                try {
                    Native.load("darkfi_mobile_ffi", NhMeshNeighborLib::class.java)
                } catch (_: Throwable) {
                    null
                }
            return neighborHandle
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
}
