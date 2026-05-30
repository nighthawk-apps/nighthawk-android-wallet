package com.nighthawkapps.lib.android.sdk.chat.darkirc

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

/**
 * Keeps the packaged **darkirc** process alive while the app may be backgrounded (foreground
 * notification required on modern Android).
 */
class DarkircDaemonService : Service() {
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
            {
                DarkircEmbeddedRunner.syncEmbeddedIrcListenConfigAndEnsureRunning(
                    applicationContext,
                )
            },
            "darkirc-start",
        ).start()
        return START_STICKY
    }

    override fun onDestroy() {
        DarkircEmbeddedRunner.stop()
        super.onDestroy()
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
            .build()

    companion object {
        private const val CHANNEL_ID = "darkirc_daemon"
        private const val NOTIFICATION_ID = 10042
    }
}
