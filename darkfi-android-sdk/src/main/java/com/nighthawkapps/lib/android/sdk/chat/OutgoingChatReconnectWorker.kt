package com.nighthawkapps.lib.android.sdk.chat

import android.content.Context
import androidx.annotation.Keep
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * When outgoing messages are queued offline, schedules a **network-backed** retry that re-invokes
 * [DarkfiChatConnectionBridge.requestReconnect] (same path as the in-app “Retry” button).
 */
@Keep
class OutgoingChatReconnectWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        DarkfiChatConnectionBridge.requestReconnect()
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "com.nighthawkapps.lib.android.chat.outgoing_reconnect"

        private const val BACKOFF_INITIAL_SECONDS = 30L

        fun schedule(context: Context) {
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            val request =
                OneTimeWorkRequestBuilder<OutgoingChatReconnectWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        BACKOFF_INITIAL_SECONDS,
                        TimeUnit.SECONDS,
                    ).build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
