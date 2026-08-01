package com.nighthawkapps.lib.android.sdk.chat.darkirc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.nighthawkapps.lib.android.sdk.R
import com.nighthawkapps.lib.android.spackle.Twig
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.stopDarkirc

/**
 * Keeps the in-process UniFFI **darkirc** daemon eligible to run while the app
 * is backgrounded. Modern Android requires an ongoing foreground notification.
 *
 * The daemon itself is started/stopped by [com.nighthawkapps.lib.android.sdk.chat.DarkfiChatController];
 * this service only raises process priority (mirrors [com.nighthawkapps.lib.android.sdk.wallet.darkfid.DarkfidDaemonService]).
 */
class DarkircDaemonService : Service() {
    @Volatile
    private var foregroundPromoted = false

    override fun onCreate() {
        super.onCreate()
        promoteToForegroundOrStop()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (!foregroundPromoted && !promoteToForegroundOrStop()) {
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    /**
     * Android 15+ caps `dataSync` foreground services (~6h / 24h). Stop promptly
     * on timeout so the system does not ANR; chat resume / coordinator will restart.
     */
    override fun onTimeout(
        startId: Int,
        fgsType: Int,
    ) {
        Twig.warn { "darkirc: dataSync FGS timed out — stopping daemon keep-alive" }
        try {
            stopDarkirc()
        } catch (e: Exception) {
            Twig.error(e) { "darkirc: stop on FGS timeout failed" }
        }
        stopSelf(startId)
    }

    override fun onDestroy() {
        foregroundPromoted = false
        isActive = false
        super.onDestroy()
    }

    /** Must run before any heavy work so Android does not ANR on missing [startForeground]. */
    private fun promoteToForegroundOrStop(): Boolean {
        if (foregroundPromoted) {
            return true
        }
        createChannelIfNeeded()
        val notification = buildNotification()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                @Suppress("DEPRECATION")
                startForeground(NOTIFICATION_ID, notification)
            }
            foregroundPromoted = true
            isActive = true
            true
        } catch (e: Exception) {
            Twig.error(e) { "darkirc: foreground service start blocked — stopping service" }
            isActive = false
            stopSelf()
            false
        }
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.darkirc_fg_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.darkirc_fg_notif_title))
            .setContentText(getString(R.string.darkirc_fg_notif_body))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    companion object {
        private const val CHANNEL_ID = "darkirc_daemon"
        private const val NOTIFICATION_ID = 10042

        @Volatile
        var isActive: Boolean = false

        fun start(context: Context) {
            if (isActive) {
                return
            }
            try {
                val intent = Intent(context, DarkircDaemonService::class.java)
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                // Background FGS starts are restricted on Android 12+; resume paths restart later.
                Twig.error(e) { "Failed to start DarkircDaemonService" }
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, DarkircDaemonService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                Twig.error(e) { "Failed to stop DarkircDaemonService" }
            }
        }
    }
}
