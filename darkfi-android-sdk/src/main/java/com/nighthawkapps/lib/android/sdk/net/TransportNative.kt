package com.nighthawkapps.lib.android.sdk.net

import android.os.SystemClock
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.transportClearSessionOverride
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.transportContinueWithoutNym
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.transportOnBackground
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.transportOnForeground
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.transportOnNetworkChanged
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.transportSetMeshEnabled
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.transportSnapshotJson
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.transportSocksAuthJson
import com.sun.jna.Library
import com.sun.jna.Native
import org.json.JSONObject

/**
 * C ABI `nh_transport_apply` (not yet in `.udl`). Loaded lazily so JVM unit
 * tests without `.so` do not fail. All other transport hooks use UniFFI.
 */
@Suppress("FunctionNaming")
internal interface NhTransportLib : Library {
    fun nh_transport_apply(
        kind: Int,
        nymPort: Int,
        torPort: Int,
    ): Int
}

data class SocksAuthCredentials(
    val username: String,
    val password: String,
)

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
        runCatching { transportOnNetworkChanged() }
    }

    fun onBackground() {
        runCatching { transportOnBackground() }
    }

    fun onForeground() {
        runCatching { transportOnForeground() }
    }

    fun setMesh(on: Boolean) {
        runCatching { transportSetMeshEnabled(on) }
    }

    fun continueWithoutNym() {
        runCatching { transportContinueWithoutNym() }
    }

    fun clearSessionOverride() {
        runCatching { transportClearSessionOverride() }
    }

    fun snapshotJson(): String? = runCatching { transportSnapshotJson() }.getOrNull()

    fun socksAuth(): SocksAuthCredentials? =
        runCatching {
            val raw = transportSocksAuthJson()
            val obj = JSONObject(raw)
            val user = obj.optString("username").takeIf { it.isNotBlank() } ?: return@runCatching null
            val pass = obj.optString("password").takeIf { it.isNotBlank() } ?: return@runCatching null
            SocksAuthCredentials(username = user, password = pass)
        }.getOrNull()

    /** `kind`: 0 Nym, 1 Tor, 2 Direct. */
    fun apply(
        kind: Int,
        nymPort: Int,
        torPort: Int,
    ) {
        runCatching { lib()?.nh_transport_apply(kind, nymPort, torPort) }
    }
}
