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
import com.nighthawkapps.lib.android.sdk.net.LocalNetworkPermission
import com.nighthawkapps.lib.android.spackle.Twig
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private const val MILLIS_PER_SECOND: Long = 1000L
private const val BULK_ACCEPT_BACKLOG: Int = 8
private const val BULK_SOCKET_TIMEOUT_MS: Int = 30_000
private const val BULK_PIPE_BUFFER_BYTES: Int = 32 * 1024

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
    internal val running = AtomicBoolean(false)
    internal val bytes = AtomicLong(0)
    internal var startedAtMs: Long = 0
    private var hotspot: WifiManager.LocalOnlyHotspotReservation? = null
    private var listener: ServerSocket? = null
    private var spliceThread: Thread? = null
    private var joinCallback: ConnectivityManager.NetworkCallback? = null

    @Volatile
    var lastOfferDest: ByteArray? = null

    fun startHostIfAllowed(fromBackgroundWorker: Boolean = false): Boolean {
        if (!BulkStartGate.permitted(context, fromBackgroundWorker)) return false
        return armHost()
    }

    private fun armHost(): Boolean {
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
        if (!BulkStartGate.joinOk(context, ssid, host, port)) return
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        val specifier =
            WifiNetworkSpecifier
                .Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(psk)
                .build()
        val request =
            NetworkRequest
                .Builder()
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
        if (!BulkQuota.accepting(this)) return false
        return BulkQuota.record(this, n)
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
        val ss =
            ServerSocket(
                NighthawkBulkPolicy.DEFAULT_LISTEN_PORT,
                BULK_ACCEPT_BACKLOG,
                InetAddress.getByName("0.0.0.0"),
            )
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
            client.soTimeout = BULK_SOCKET_TIMEOUT_MS
            Socket(host, port).use { remote ->
                val up =
                    Thread {
                        copy(client, remote)
                    }
                val down =
                    Thread {
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
        val buf = ByteArray(BULK_PIPE_BUFFER_BYTES)
        try {
            val input = from.getInputStream()
            val output = to.getOutputStream()
            while (running.get()) {
                if (!BulkPipe.transferChunk(this, input, output, buf)) break
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
                            val sid = ByteArray(MeshNative.MESH_CORRELATION_BYTES)
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

private object BulkStartGate {
    fun permitted(
        context: Context,
        fromBackgroundWorker: Boolean,
    ): Boolean {
        if (!policyAllows(fromBackgroundWorker)) return false
        return permissions(context)
    }

    fun joinOk(
        context: Context,
        ssid: String,
        host: String,
        port: Int,
    ): Boolean {
        if (ssid.isEmpty() || host.isEmpty()) return false
        return portAndLocal(context, port)
    }

    private fun policyAllows(fromBackgroundWorker: Boolean): Boolean =
        NighthawkBulkPolicy.mayStart(
            foreground = true,
            gatewayReady = MeshCoordinator.gatewayOptIn,
            fromBackgroundWorker = fromBackgroundWorker,
        )

    private fun permissions(context: Context): Boolean {
        if (!nearbyWifi(context)) return false
        return localNetwork(context, "mesh: ACCESS_LOCAL_NETWORK missing for bulk")
    }

    private fun nearbyWifi(context: Context): Boolean {
        if (MeshPermissionGate.hasNearbyWifiPermission(context)) return true
        Twig.warn { "mesh: nearby wifi permission missing for bulk" }
        return false
    }

    private fun portAndLocal(
        context: Context,
        port: Int,
    ): Boolean {
        if (port <= 0) return false
        return localNetwork(context, "mesh: ACCESS_LOCAL_NETWORK missing for bulk join")
    }

    private fun localNetwork(
        context: Context,
        denialMessage: String,
    ): Boolean {
        if (LocalNetworkPermission.hasGranted(context)) return true
        Twig.warn { denialMessage }
        return false
    }
}

private object BulkQuota {
    fun accepting(link: NighthawkBulkLink): Boolean {
        if (!link.running.get()) return false
        return withinTime(link)
    }

    fun record(
        link: NighthawkBulkLink,
        n: Long,
    ): Boolean {
        val total = link.bytes.addAndGet(n)
        if (underCap(total) && MeshNative.recordBulkBytes(n)) return true
        link.stop()
        return false
    }

    private fun withinTime(link: NighthawkBulkLink): Boolean {
        val elapsed = (System.currentTimeMillis() - link.startedAtMs) / MILLIS_PER_SECOND
        if (elapsed <= NighthawkBulkPolicy.MAX_SECS) return true
        link.stop()
        return false
    }

    private fun underCap(total: Long): Boolean = total <= NighthawkBulkPolicy.MAX_BYTES
}

private object BulkPipe {
    fun transferChunk(
        link: NighthawkBulkLink,
        input: InputStream,
        output: OutputStream,
        buf: ByteArray,
    ): Boolean {
        val n = input.read(buf)
        if (n <= 0) return false
        return forward(link, output, buf, n)
    }

    private fun forward(
        link: NighthawkBulkLink,
        output: OutputStream,
        buf: ByteArray,
        n: Int,
    ): Boolean {
        if (!link.noteBytes(n.toLong())) return false
        output.write(buf, 0, n)
        output.flush()
        return true
    }
}
