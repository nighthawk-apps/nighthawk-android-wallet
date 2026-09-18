package com.nighthawkapps.lib.android.sdk.mesh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.nighthawkapps.lib.android.sdk.R
import com.nighthawkapps.lib.android.spackle.Twig

/**
 * `connectedDevice` FGS — not [com.nighthawkapps.lib.android.sdk.chat.darkirc.DarkircDaemonService]
 * (`remoteMessaging`) and not darkfid (`dataSync`, 6h timeout on API 35+).
 */
class NighthawkMeshService : Service() {
    @Volatile
    private var foregroundPromoted = false
    private var radio: NighthawkBleLink? = null
    private val inbox = MeshFrameInbox()
    private var connectivity: ConnectivityManager? = null
    private var appForeground = true
    private val dagHandler = Handler(Looper.getMainLooper())
    private val dagTick =
        object : Runnable {
            override fun run() {
                if (!isActive) return
                if (!MeshPermissionGate.hasBlePermissions(this@NighthawkMeshService)) {
                    Twig.warn { "mesh: BLE permission revoked — stopping" }
                    userStop()
                    return
                }
                MeshNative.requestDagSync()
                MeshNative.flushOutbound { bytes -> radio?.send(bytes) }
                refreshNotification()
                dagHandler.postDelayed(this, DAG_SYNC_INTERVAL_MS)
            }
        }
    private val networkCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                applyPower()
            }

            override fun onLost(network: Network) {
                applyPower()
            }
        }

    private val batteryReceiver =
        object : android.content.BroadcastReceiver() {
            override fun onReceive(
                context: android.content.Context?,
                intent: Intent?,
            ) {
                applyPower()
            }
        }

    private val lifecycleObserver =
        LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    appForeground = true
                    ensureRadio()
                    applyPower()
                }
                Lifecycle.Event.ON_STOP -> {
                    appForeground = false
                    applyPower()
                    if (!MeshCoordinator.alwaysOn) {
                        Twig.debug { "mesh: pausing FGS — always-on is off" }
                        stopSelf()
                    }
                }
                else -> Unit
            }
        }

    private val engineSink =
        MeshLinkSink { frame ->
            if (!inbox.shouldIngest(frame)) return@MeshLinkSink
            MeshNative.ingest(frame)
            MeshNative.drainEngineEvents()
            val r = radio
            MeshNative.flushOutbound { bytes -> r?.send(bytes) }
        }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        runningContext = this
        MeshCoordinator.bind(this)
        appForeground =
            ProcessLifecycleOwner.get().lifecycle.currentState
                .isAtLeast(Lifecycle.State.STARTED)
        promoteToForegroundOrStop()
        if (foregroundPromoted) {
            ensureRadio()
            registerPowerListeners()
            dagHandler.post(dagTick)
            Handler(Looper.getMainLooper()).post {
                try {
                    ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
                } catch (_: Exception) {
                }
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_STOP) {
            userStop()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_POWER) {
            applyPower()
            return stickyFlag()
        }
        if (!MeshPermissionGate.isMeshSdkSupported()) {
            Twig.warn { "mesh: SDK ${Build.VERSION.SDK_INT} < 31 — stopping" }
            stopSelf()
            return START_NOT_STICKY
        }
        if (!foregroundPromoted && !promoteToForegroundOrStop()) {
            return START_NOT_STICKY
        }
        ensureRadio()
        applyPower()
        return stickyFlag()
    }

    override fun onTimeout(
        startId: Int,
        fgsType: Int,
    ) {
        Twig.warn { "mesh: FGS timed out — stopping radio keep-alive" }
        stopSelf(startId)
    }

    override fun onDestroy() {
        dagHandler.removeCallbacks(dagTick)
        try {
            ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        } catch (_: Exception) {
        }
        radio?.stop()
        radio = null
        unregisterPowerListeners()
        MeshNative.stop()
        foregroundPromoted = false
        isActive = false
        if (runningContext === this) {
            runningContext = null
        }
        super.onDestroy()
    }

    private fun userStop() {
        MeshPersist.setEnabled(this, false)
        stopSelf()
    }

    private fun stickyFlag(): Int =
        if (MeshCoordinator.stickyRestart()) START_STICKY else START_NOT_STICKY

    private fun ensureRadio() {
        if (!foregroundPromoted) return
        if (!MeshPermissionGate.hasBlePermissions(this)) return
        if (radio != null) return
        MeshNative.start()
        MeshNative.setGatewayEligible(false)
        val link = NighthawkBleLink(this, engineSink)
        link.onEngineFlush = { MeshNative.flushOutbound { bytes -> link.send(bytes) } }
        radio = link
        link.start()
        applyPower()
    }

    private fun promoteToForegroundOrStop(): Boolean {
        if (foregroundPromoted) return true
        createChannelIfNeeded()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
                )
            } else {
                @Suppress("DEPRECATION")
                startForeground(NOTIFICATION_ID, buildNotification())
            }
            foregroundPromoted = true
            isActive = true
            true
        } catch (e: Exception) {
            Twig.error(e) { "mesh: foreground service start blocked" }
            isActive = false
            stopSelf()
            false
        }
    }

    private fun registerPowerListeners() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(
                    batteryReceiver,
                    IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                    RECEIVER_NOT_EXPORTED,
                )
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            }
        } catch (_: Exception) {
        }
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        connectivity = cm
        try {
            cm.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build(),
                networkCallback,
            )
        } catch (_: Exception) {
        }
    }

    private fun unregisterPowerListeners() {
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {
        }
        try {
            connectivity?.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {
        }
        connectivity = null
    }

    private fun applyPower() {
        val charging =
            MeshEnvironment.isCharging(
                registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)),
            )
        val cm = getSystemService(ConnectivityManager::class.java)
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
        val unmetered = MeshEnvironment.isUnmeteredWifi(caps)
        MeshNative.setPower(appForeground, charging, unmetered)
        radio?.setPower(
            appForeground,
            charging,
            MeshPowerPolicy.gatewayReady(
                meshOn = true,
                gatewayOptIn = MeshCoordinator.gatewayOptIn,
                charging = charging,
                unmetered = unmetered,
            ),
            alwaysOn = MeshCoordinator.alwaysOn,
        )
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.mesh_fg_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun refreshNotification() {
        if (!foregroundPromoted) return
        try {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, buildNotification())
        } catch (_: Exception) {
        }
    }

    private fun buildNotification(): Notification {
        val stop =
            PendingIntent.getService(
                this,
                0,
                Intent(this, NighthawkMeshService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val launch =
            packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                PendingIntent.getActivity(
                    this,
                    1,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }
        val peers = radio?.peerCount ?: 0
        hudPeerCount = peers
        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.mesh_fg_notif_title))
            .setContentText(getString(R.string.mesh_fg_notif_peers, peers))
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(launch)
            .addAction(0, getString(R.string.mesh_fg_stop), stop)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "nighthawk_mesh"
        private const val NOTIFICATION_ID = 10086
        private const val DAG_SYNC_INTERVAL_MS = 15_000L
        const val ACTION_STOP: String = "com.nighthawkapps.mesh.STOP"
        const val ACTION_POWER: String = "com.nighthawkapps.mesh.POWER"

        @Volatile
        var isActive: Boolean = false
            private set

        @Volatile
        var hudPeerCount: Int = 0
            private set

        @Volatile
        private var runningContext: Context? = null

        fun startFromForeground(context: Context): Boolean {
            val activity = context.findActivity()
            if (activity == null) {
                Twig.warn { "mesh: FGS start refused — not an Activity" }
                return false
            }
            if (!MeshPermissionGate.isMeshSdkSupported()) return false
            if (isActive) {
                notifyPowerChanged()
                return true
            }
            return try {
                ContextCompat.startForegroundService(
                    activity,
                    Intent(activity, NighthawkMeshService::class.java),
                )
                true
            } catch (e: Exception) {
                Twig.error(e) { "mesh: cannot start FGS from this process state" }
                false
            }
        }

        fun startFromBootExemption(context: Context): Boolean {
            if (!MeshPermissionGate.isMeshSdkSupported()) return false
            if (isActive) return true
            return try {
                ContextCompat.startForegroundService(
                    context.applicationContext,
                    Intent(context.applicationContext, NighthawkMeshService::class.java),
                )
                true
            } catch (e: Exception) {
                Twig.error(e) { "mesh: boot FGS start blocked" }
                false
            }
        }

        fun notifyPowerChanged() {
            val ctx = runningContext ?: return
            try {
                ctx.startService(
                    Intent(ctx, NighthawkMeshService::class.java).setAction(ACTION_POWER),
                )
            } catch (_: Exception) {
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, NighthawkMeshService::class.java))
            } catch (e: Exception) {
                Twig.error(e) { "mesh: stop failed" }
            }
        }
    }
}
