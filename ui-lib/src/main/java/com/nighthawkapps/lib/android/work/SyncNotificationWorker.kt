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
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkerParameters
import com.nighthawkapps.lib.android.spackle.Twig
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.WORKER_TAG_SYNC_NOTIFICATION
import java.util.Calendar
import java.util.concurrent.TimeUnit

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

        val notification =
            NotificationCompat.Builder(appContext, WorkIds.SYNC_NOTIFICATION_CHANNEL_ID).apply {
                setContentIntent(pendingIntent)
            }
        notification.setContentTitle(appContext.getString(R.string.ns_sync_notifications_text))
        notification.setContentText(appContext.getString(R.string.ns_sync_notifications_body))
        notification.priority = NotificationCompat.PRIORITY_HIGH
        notification.setCategory(NotificationCompat.CATEGORY_ALARM)
        notification.setSmallIcon(R.drawable.ic_nighthawk_logo)
        notification.setAutoCancel(true)
        val vibrate = longArrayOf(0, 100, 200, 300)
        notification.setVibrate(vibrate)

        with(NotificationManagerCompat.from(appContext)) {
            if (ActivityCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            notify(2, notification.build())
        }
    }

    private fun createNotificationChannel() {
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channelPeriodic = NotificationChannel(WorkIds.SYNC_NOTIFICATION_CHANNEL_ID, WorkIds.SYNC_NOTIFICATION_CHANNEL_NAME, importance)
        channelPeriodic.description = WorkIds.SYNC_NOTIFICATION_CHANNEL_DESC
        val notificationManager = NotificationManagerCompat.from(appContext)
        notificationManager.createNotificationChannel(channelPeriodic)
    }

    companion object {
        fun newWorkRequest(frequencyInDays: Long): PeriodicWorkRequest {
            val myConstraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            val currentDate = Calendar.getInstance()
            val dueDate = Calendar.getInstance()

            // Set Execution around 07:00:00 AM
            dueDate.set(Calendar.HOUR_OF_DAY, 7)
            dueDate.set(Calendar.MINUTE, 0)
            dueDate.set(Calendar.SECOND, 0)
            dueDate.add(Calendar.DAY_OF_YEAR, frequencyInDays.toInt())
            if (dueDate.before(currentDate)) {
                dueDate.add(Calendar.HOUR_OF_DAY, 24)
            }

            val timeDiff = dueDate.timeInMillis - currentDate.timeInMillis
            val minutes = TimeUnit.MILLISECONDS.toMinutes(timeDiff)

            return PeriodicWorkRequest
                .Builder(SyncNotificationWorker::class.java, frequencyInDays, TimeUnit.DAYS)
                .setConstraints(myConstraints)
                .setInitialDelay(minutes, TimeUnit.MINUTES)
                .addTag(WORKER_TAG_SYNC_NOTIFICATION)
                .build()
        }
    }
}
