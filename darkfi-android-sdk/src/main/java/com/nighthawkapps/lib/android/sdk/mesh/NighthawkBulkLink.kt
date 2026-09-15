package com.nighthawkapps.lib.android.sdk.mesh

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import com.nighthawkapps.lib.android.spackle.Twig
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Quotas and start gates for UnifOMR bulk. Radios never start from a worker. */
object NighthawkBulkPolicy {
    const val MAX_BYTES: Long = 200L * 1024L * 1024L
    const val MAX_SECS: Long = 15L * 60L
    const val KIND_AWARE: Int = 1
    const val KIND_SOFTAP: Int = 2
    const val SERVICE_NAME: String = "nighthawk-mesh"
    const val DEFAULT_LISTEN_PORT: Int = 18443

    fun mayStart(
        foreground: Boolean,
        gatewayReady: Boolean,
        fromBackgroundWorker: Boolean,
    ): Boolean =
        MeshPowerPolicy.mayStartUnifOmrBulk(
            foreground = foreground,
            gatewayReady = gatewayReady,
            fromBackgroundWorker = fromBackgroundWorker,
        )
}

/**
 * Wi-Fi Aware (Android–Android) or LocalOnlyHotspot host / specifier join.
 * Does not call [ConnectivityManager.bindProcessToNetwork].
 */
@RequiresApi(Build.VERSION_CODES.S)
class NighthawkBulkLink(
    private val context: Context,
) {
    private val main = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private val bytes = AtomicLong(0)
    private var startedAtMs: Long = 0
    private var hotspot: WifiManager.LocalOnlyHotspotReservation? = null
    private var listener: ServerSocket? = null
    private var spliceThread: Thread? = null
    private var joinCallback: ConnectivityManager.NetworkCallback? = null

    @Volatile
    var lastOfferDest: ByteArray? = null

    fun startHostIfAllowed(fromBackgroundWorker: Boolean = false): Boolean {
        if (!NighthawkBulkPolicy.mayStart(
                foreground = true,
                gatewayReady = MeshCoordinator.gatewayOptIn,
                fromBackgroundWorker = fromBackgroundWorker,
            )
        ) {
            return false
        }
        if (!MeshPermissionGate.hasNearbyWifiPermission(context)) {
            Twig.warn { "mesh: nearby wifi permission missing for bulk" }
            return false
        }
        if (!running.compareAndSet(false, true)) return true
        startedAtMs = System.currentTimeMillis()
        bytes.set(0)
        return try {
            startListenerLocked()
            startSoftApLocked()
            true
        } catch (_: Exception) {
            stop()
            false
        }
    }

    fun join(
        ssid: String,
        psk: String,
        host: String,
        port: Int,
    ) {
        if (ssid.isEmpty() || host.isEmpty() || port <= 0) return
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        val specifier =
            WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(psk)
                .build()
        val request =
            NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier)
                .build()
        val cb =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    MeshNative.setBulkTcp(host, port)
                }

                override fun onLost(network: Network) {
                    MeshNative.setBulkTcp(null, 0)
                }
            }
        joinCallback = cb
        try {
            cm.requestNetwork(request, cb)
        } catch (_: Exception) {
            Twig.warn { "mesh: bulk join request failed" }
        }
    }

    fun noteBytes(n: Long): Boolean {
        if (!running.get()) return false
        val elapsed = (System.currentTimeMillis() - startedAtMs) / 1000L
        if (elapsed > NighthawkBulkPolicy.MAX_SECS) {
            stop()
            return false
        }
        val total = bytes.addAndGet(n)
        if (total > NighthawkBulkPolicy.MAX_BYTES || !MeshNative.recordBulkBytes(n)) {
            stop()
            return false
        }
        return true
    }

    fun stop() {
        running.set(false)
        MeshNative.setBulkTcp(null, 0)
        try {
            spliceThread?.interrupt()
        } catch (_: Exception) {
        }
        spliceThread = null
        try {
            listener?.close()
        } catch (_: Exception) {
        }
        listener = null
        try {
            hotspot?.close()
        } catch (_: Exception) {
        }
        hotspot = null
        val cm = context.getSystemService(ConnectivityManager::class.java)
        joinCallback?.let { cb ->
            try {
                cm?.unregisterNetworkCallback(cb)
            } catch (_: Exception) {
            }
        }
        joinCallback = null
    }

    private fun startListenerLocked() {
        val host = MeshCoordinator.lwdSpliceHost ?: return
        val port = MeshCoordinator.lwdSplicePort
        val ss = ServerSocket(NighthawkBulkPolicy.DEFAULT_LISTEN_PORT, 8, InetAddress.getByName("0.0.0.0"))
        listener = ss
        spliceThread =
            Thread({
                while (running.get()) {
                    try {
                        val client = ss.accept()
                        Thread({ pipe(client, host, port) }, "nh-mesh-bulk-splice").apply {
                            isDaemon = true
                            start()
                        }
                    } catch (_: Exception) {
                        if (!running.get()) break
                    }
                }
            }, "nh-mesh-bulk-listen").apply {
                isDaemon = true
                start()
            }
    }

    private fun pipe(
        client: Socket,
        host: String,
        port: Int,
    ) {
        try {
            client.soTimeout = 30_000
            Socket(host, port).use { remote ->
                val up = Thread {
                    copy(client, remote)
                }
                val down = Thread {
                    copy(remote, client)
                }
                up.isDaemon = true
                down.isDaemon = true
                up.start()
                down.start()
                up.join()
                down.join()
            }
        } catch (_: Exception) {
        } finally {
            try {
                client.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun copy(
        from: Socket,
        to: Socket,
    ) {
        val buf = ByteArray(32 * 1024)
        try {
            val input = from.getInputStream()
            val output = to.getOutputStream()
            while (running.get()) {
                val n = input.read(buf)
                if (n <= 0) break
                if (!noteBytes(n.toLong())) break
                output.write(buf, 0, n)
                output.flush()
            }
        } catch (_: Exception) {
        }
    }

    private fun startSoftApLocked() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Twig.warn { "mesh: local hotspot host needs API 33 (no location)" }
            running.set(false)
            return
        }
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        main.post {
            try {
                wifi.startLocalOnlyHotspot(
                    object : WifiManager.LocalOnlyHotspotCallback() {
                        override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                            hotspot = reservation
                            @Suppress("DEPRECATION")
                            val cfg = reservation.wifiConfiguration
                            val ssid = cfg?.SSID?.trim('"').orEmpty()
                            val psk = cfg?.preSharedKey?.trim('"').orEmpty()
                            val dest = lastOfferDest ?: return
                            val sid = ByteArray(16)
                            SecureRandom().nextBytes(sid)
                            MeshNative.submitBulkOffer(
                                dest,
                                NighthawkBulkPolicy.KIND_SOFTAP,
                                NighthawkBulkPolicy.DEFAULT_LISTEN_PORT,
                                ssid,
                                psk.toByteArray(Charsets.UTF_8),
                                sid,
                            )
                        }

                        override fun onFailed(reason: Int) {
                            Twig.warn { "mesh: local hotspot failed" }
                            running.set(false)
                        }
                    },
                    main,
                )
            } catch (_: Exception) {
                Twig.warn { "mesh: local hotspot unavailable" }
                running.set(false)
            }
        }
    }
}
