package com.nighthawkapps.lib.android.sdk.wallet.darkfid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.nighthawkapps.lib.android.sdk.R

/** Keeps packaged **darkfid** alive while the wallet syncs in the background. */
class DarkfidDaemonService : Service() {
    override fun onBind(intent: android.content.Intent?): IBinder? = null

    override fun onStartCommand(
        intent: android.content.Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        createChannelIfNeeded()
        val notification = buildNotification()
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
        Thread(
            { DarkfidEmbeddedRunner.syncConfigAndEnsureRunning(applicationContext) },
            "darkfid-start",
        ).start()
        return START_STICKY
    }

    /**
     * Android 15+ caps `dataSync` foreground services at ~6 hours / 24h.
     * Stop promptly on timeout so the system does not ANR the app; WorkManager /
     * foreground resume paths can restart the daemon later.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        DarkfidEmbeddedRunner.stop()
        stopSelf(startId)
    }

    override fun onDestroy() {
        DarkfidEmbeddedRunner.stop()
        super.onDestroy()
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.darkfid_fg_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.darkfid_fg_notif_title))
            .setContentText(getString(R.string.darkfid_fg_notif_body))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()

    companion object {
        private const val CHANNEL_ID = "darkfid_daemon"
        private const val NOTIFICATION_ID = 10043
    }
}
