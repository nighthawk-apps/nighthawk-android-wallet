package com.nighthawkapps.lib.android.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkerParameters
import com.nighthawkapps.lib.android.spackle.Twig
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.WORKER_TAG_SYNC_NOTIFICATION
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Periodic reminder to open the wallet and sync.
 *
 * Use case: when the app sits closed for days/weeks, startup sync takes longer.
 * Reminding the user to open Nighthawk keeps the wallet warm — no network is
 * required to *deliver* the reminder itself.
 */
class SyncNotificationWorker(
    private val appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        Twig.debug { "Sync notification periodic worker invoked" }
        showNotification()
        return Result.success()
    }

    private fun showNotification() {
        createNotificationChannel()
        val intent =
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        val pendingIntent =
            PendingIntent.getActivity(
                appContext,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

        val title = appContext.getString(R.string.ns_sync_notification_push_title)
        val body = appContext.getString(R.string.ns_sync_notification_push_body)
        val notification =
            NotificationCompat
                .Builder(appContext, WorkIds.SYNC_NOTIFICATION_CHANNEL_ID)
                .setContentIntent(pendingIntent)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setSmallIcon(R.drawable.ic_nighthawk_logo)
                .setAutoCancel(true)
                .build()

        with(NotificationManagerCompat.from(appContext)) {
            if (ActivityCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                Twig.warn { "Sync reminder skipped: POST_NOTIFICATIONS not granted" }
                return
            }
            notify(SYNC_REMINDER_NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channelPeriodic =
            NotificationChannel(
                WorkIds.SYNC_NOTIFICATION_CHANNEL_ID,
                WorkIds.SYNC_NOTIFICATION_CHANNEL_NAME,
                importance
            )
        channelPeriodic.description = WorkIds.SYNC_NOTIFICATION_CHANNEL_DESC
        NotificationManagerCompat.from(appContext).createNotificationChannel(channelPeriodic)
    }

    companion object {
        private const val SYNC_REMINDER_NOTIFICATION_ID = 2

        fun newWorkRequest(frequencyInDays: Long): PeriodicWorkRequest {
            val currentDate = Calendar.getInstance()
            val dueDate = Calendar.getInstance()

            // First fire around 07:00 after [frequencyInDays], then repeat.
            dueDate.set(Calendar.HOUR_OF_DAY, 7)
            dueDate.set(Calendar.MINUTE, 0)
            dueDate.set(Calendar.SECOND, 0)
            dueDate.add(Calendar.DAY_OF_YEAR, frequencyInDays.toInt())
            if (dueDate.before(currentDate)) {
                dueDate.add(Calendar.DAY_OF_YEAR, 1)
            }

            val timeDiff = dueDate.timeInMillis - currentDate.timeInMillis
            val minutes = TimeUnit.MILLISECONDS.toMinutes(timeDiff).coerceAtLeast(1)

            return PeriodicWorkRequest
                .Builder(SyncNotificationWorker::class.java, frequencyInDays, TimeUnit.DAYS)
                .setInitialDelay(minutes, TimeUnit.MINUTES)
                .addTag(WORKER_TAG_SYNC_NOTIFICATION)
                .build()
        }
    }
}
