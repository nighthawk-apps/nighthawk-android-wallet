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
import com.nighthawkapps.lib.android.sdk.chat.darkirc.DarkircDaemonBootstrap
import com.nighthawkapps.lib.android.sdk.chat.darkirc.DarkircEmbeddedRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * When outgoing messages are queued offline, schedules a **network-backed** retry that re-invokes
 * [DarkfiChatConnectionBridge.requestReconnect] (same path as the in-app “Retry” button).
 *
 * If embedded DarkIRC is enabled on loopback, prepares the daemon before reconnect (see
 * [DarkircDaemonBootstrap.prepareForLoopbackIrc]).
 */
@Keep
class OutgoingChatReconnectWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        withContext(Dispatchers.IO) {
            prepareEmbeddedDarkircIfApplicable()
        }
        DarkfiChatConnectionBridge.requestReconnect()
        return Result.success()
    }

    private fun prepareEmbeddedDarkircIfApplicable() {
        val app = applicationContext
        val prefs = DarkfiChatPreferences(app)
        if (!prefs.runEmbeddedDarkirc || !DarkircEmbeddedRunner.hasBundledBinary(app)) {
            return
        }
        val host = prefs.ircServerHost.trim().ifBlank { "127.0.0.1" }
        val loopback =
            host == "127.0.0.1" ||
                host.equals("localhost", ignoreCase = true) ||
                host == "::1"
        if (loopback) {
            DarkircDaemonBootstrap.prepareForLoopbackIrc(app)
        } else {
            DarkircDaemonBootstrap.warmEmbeddedOnForeground(app)
        }
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
