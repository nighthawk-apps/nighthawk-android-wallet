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
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiSynchronizer
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration

@Keep
class SyncWorker(
    context: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters) {
    override suspend fun doWork(): Result {
        AppWalletCoordinator.setBackgroundSyncActive(true)
        return try {
            runSyncAttempt(
                synchronizerFlow = AppWalletCoordinator.get(applicationContext).synchronizer,
            )
        } finally {
            AppWalletCoordinator.setBackgroundSyncActive(false)
        }
    }

    companion object {
        private val DEFAULT_SYNC_PERIOD = 24.hours
        internal const val SYNCHRONIZER_WAIT_MS = 5_000L
        internal const val SYNC_TIMEOUT_MS = 120_000L

        internal suspend fun runSyncAttempt(
            synchronizerFlow: StateFlow<DarkfiSynchronizer?>,
            synchronizerWaitMs: Long = SYNCHRONIZER_WAIT_MS,
            syncTimeoutMs: Long = SYNC_TIMEOUT_MS,
        ): Result {
            val sync =
                withTimeoutOrNull(synchronizerWaitMs) {
                    synchronizerFlow.filterNotNull().first()
                }
            val outcome =
                if (sync == null) {
                    null
                } else {
                    withTimeoutOrNull(syncTimeoutMs) {
                        sync.refreshNow()
                        combine(
                            sync.status,
                            sync.progress,
                            sync.processorInfo,
                        ) { status, progress, info ->
                            SyncAttemptTick(status, progress, info.chainTip)
                        }.first { tick ->
                            isTerminalTick(tick)
                        }
                    }
                }

            return when (outcome?.status) {
                DarkfiSyncStatus.PROTO_MISMATCH -> {
                    Result.failure()
                }

                DarkfiSyncStatus.SYNCED -> {
                    if (outcome.chainTip > 0L && !outcome.progress.isLessThanHundredPercent()) {
                        Result.success()
                    } else {
                        Result.retry()
                    }
                }

                else -> {
                    Result.retry()
                }
            }
        }

        private fun isTerminalTick(tick: SyncAttemptTick): Boolean =
            when (tick.status) {
                DarkfiSyncStatus.ERROR,
                DarkfiSyncStatus.DEGRADED,
                DarkfiSyncStatus.DISCONNECTED,
                DarkfiSyncStatus.STOPPED,
                DarkfiSyncStatus.PROTO_MISMATCH,
                -> {
                    true
                }

                DarkfiSyncStatus.SYNCED -> {
                    tick.chainTip <= 0L || !tick.progress.isLessThanHundredPercent()
                }

                else -> {
                    false
                }
            }

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

private data class SyncAttemptTick(
    val status: DarkfiSyncStatus,
    val progress: DarkfiPercent,
    val chainTip: Long,
)
