package com.nighthawkapps.lib.android.sdk.net

import android.os.SystemClock
import com.sun.jna.Library
import com.sun.jna.Native

/**
 * C ABI `nh_transport_*` from the shared FFI crate. Loaded on its own so a
 * missing symbol in an older `libdarkfi_mobile_ffi.so` does not break mesh.
 */
@Suppress("FunctionNaming")
internal interface NhTransportLib : Library {
    fun nh_transport_on_network_changed(): Int

    fun nh_transport_on_background(): Int

    fun nh_transport_on_foreground(): Int

    fun nh_transport_set_mesh(on: Int): Int

    fun nh_transport_continue_without_nym(): Int

    fun nh_transport_clear_session_override(): Int

    fun nh_transport_apply(
        kind: Int,
        nymPort: Int,
        torPort: Int,
    ): Int
}

object TransportNative {
    private val lock = Any()

    @Volatile
    private var tried = false

    @Volatile
    private var libHandle: NhTransportLib? = null

    @Volatile
    private var lastNetworkChangeMs: Long = 0L

    private fun lib(): NhTransportLib? {
        synchronized(lock) {
            if (tried) return libHandle
            tried = true
            libHandle =
                try {
                    Native.load("darkfi_mobile_ffi", NhTransportLib::class.java)
                } catch (_: Throwable) {
                    null
                }
            return libHandle
        }
    }

    fun onNetworkChanged() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastNetworkChangeMs < 2_000L) return
        lastNetworkChangeMs = now
        runCatching { lib()?.nh_transport_on_network_changed() }
    }

    fun onBackground() {
        runCatching { lib()?.nh_transport_on_background() }
    }

    fun onForeground() {
        runCatching { lib()?.nh_transport_on_foreground() }
    }

    fun setMesh(on: Boolean) {
        runCatching { lib()?.nh_transport_set_mesh(if (on) 1 else 0) }
    }

    fun continueWithoutNym() {
        runCatching { lib()?.nh_transport_continue_without_nym() }
    }

    fun clearSessionOverride() {
        runCatching { lib()?.nh_transport_clear_session_override() }
    }

    /** `kind`: 0 Nym, 1 Tor, 2 Direct. */
    fun apply(
        kind: Int,
        nymPort: Int,
        torPort: Int,
    ) {
        runCatching { lib()?.nh_transport_apply(kind, nymPort, torPort) }
    }
}
