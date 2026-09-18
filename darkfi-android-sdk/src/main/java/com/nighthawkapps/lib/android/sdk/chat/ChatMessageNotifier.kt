package com.nighthawkapps.lib.android.sdk.chat

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.Lifecycle
import com.nighthawkapps.lib.android.sdk.R
import com.nighthawkapps.lib.android.spackle.Twig

/** Chat alerts while the app is backgrounded (parity with iOS notifications). */
internal object ChatMessageNotifier {
    private const val CHANNEL_ID = "darkirc_messages"
    private const val NOTIFICATION_ID = 10043

    fun maybeNotify(
        context: Context,
        channel: String,
        nick: String,
        preview: String,
    ) {
        val app = context.applicationContext
        val foreground =
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (foreground) return
        if (Build.VERSION.SDK_INT >= 33) {
            val granted =
                ContextCompat.checkSelfPermission(
                    app,
                    android.Manifest.permission.POST_NOTIFICATIONS,
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        createChannel(app)
        val launch =
            app.packageManager.getLaunchIntentForPackage(app.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        val pi =
            launch?.let {
                PendingIntent.getActivity(
                    app,
                    0,
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }
        val text = "$nick: ${preview.take(80)}"
        val notif =
            NotificationCompat
                .Builder(app, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(channel)
                .setContentText(text)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .build()
        runCatching { NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, notif) }
            .onFailure { Twig.warn { "chat notify skipped: ${it.message}" } }
    }

    private fun createChannel(app: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                app.getString(R.string.darkirc_fg_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }
}
