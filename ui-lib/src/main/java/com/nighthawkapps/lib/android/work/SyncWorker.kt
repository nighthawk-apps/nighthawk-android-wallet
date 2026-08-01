package com.nighthawkapps.lib.android.work

import android.content.Context
import androidx.annotation.Keep
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import com.nighthawkapps.lib.android.global.AppWalletCoordinator
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiPercent
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiSyncStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.takeWhile
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration

@Keep
class SyncWorker(
    context: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters) {
    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun doWork(): Result {
        AppWalletCoordinator
            .get(applicationContext)
            .synchronizer
            .flatMapLatest { sync ->
                sync?.let { s ->
                    combine(s.status, s.progress) { status, progress ->
                        StatusAndProgress(status, progress)
                    }
                } ?: emptyFlow()
            }.takeWhile {
                it.status != DarkfiSyncStatus.DISCONNECTED && it.progress.isLessThanHundredPercent()
            }.collect()

        return Result.success()
    }

    companion object {
        private val DEFAULT_SYNC_PERIOD = 24.hours

        fun newWorkRequest(): PeriodicWorkRequest {
            val constraints =
                Constraints
                    .Builder()
                    .setRequiresStorageNotLow(true)
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            return PeriodicWorkRequestBuilder<SyncWorker>(DEFAULT_SYNC_PERIOD.toJavaDuration())
                .setConstraints(constraints)
                .build()
        }
    }
}

private data class StatusAndProgress(
    val status: DarkfiSyncStatus,
    val progress: DarkfiPercent
)
