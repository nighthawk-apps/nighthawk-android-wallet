package com.nighthawkapps.lib.android.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import com.nighthawkapps.lib.android.ui.common.WORKER_TAG_SYNC_NOTIFICATION
import com.nighthawkapps.lib.android.ui.preference.EncryptedPreferenceKeys.SYNC_INTERVAL_OPTION
import com.nighthawkapps.lib.android.ui.preference.StandardPreferenceSingleton
import com.nighthawkapps.lib.android.ui.screen.syncnotification.viewmodel.SyncNotificationViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object WorkIds {
    const val WORK_ID_BACKGROUND_SYNC = "com.nighthawkapps.lib.android.background_sync"
    const val SYNC_NOTIFICATION_CHANNEL_ID = "syncNotificationChannelId"
    const val SYNC_NOTIFICATION_CHANNEL_NAME = "Sync reminders"
    const val SYNC_NOTIFICATION_CHANNEL_DESC = "Reminders to open Nighthawk and sync your wallet"

    fun enableBackgroundSynchronization(context: Context) {
        val workManager = WorkManager.getInstance(context)

        workManager.enqueueUniquePeriodicWork(
            WORK_ID_BACKGROUND_SYNC,
            ExistingPeriodicWorkPolicy.KEEP,
            SyncWorker.newWorkRequest()
        )
    }

    fun disableBackgroundSynchronization(context: Context) {
        val workManager = WorkManager.getInstance(context)

        workManager.cancelUniqueWork(WORK_ID_BACKGROUND_SYNC)
    }

    /**
     * Ensure the sync-reminder worker matches the saved preference.
     * Uses KEEP so app start does not reset the next fire time.
     */
    fun ensureSyncNotificationScheduled(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val preferenceProvider = StandardPreferenceSingleton.getInstance(context)
            val option = SYNC_INTERVAL_OPTION.getValue(preferenceProvider)
            if (option == SyncNotificationViewModel.SyncIntervalOption.OFF) {
                cancelSyncNotificationWork(context)
            } else {
                startPeriodicSyncNotificationWork(
                    context,
                    option.interval.toLong(),
                    ExistingPeriodicWorkPolicy.KEEP
                )
            }
        }
    }

    fun cancelSyncAppNotificationAndReRegister(
        syncIntervalOption: SyncNotificationViewModel.SyncIntervalOption,
        context: Context
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            cancelSyncNotificationWork(context)
            if (syncIntervalOption != SyncNotificationViewModel.SyncIntervalOption.OFF) {
                startPeriodicSyncNotificationWork(
                    context,
                    syncIntervalOption.interval.toLong(),
                    ExistingPeriodicWorkPolicy.UPDATE
                )
            }
        }
    }

    private fun cancelSyncNotificationWork(appContext: Context) {
        WorkManager.getInstance(appContext).cancelAllWorkByTag(WORKER_TAG_SYNC_NOTIFICATION)
        WorkManager.getInstance(appContext).cancelUniqueWork(WORKER_TAG_SYNC_NOTIFICATION)
    }

    private fun startPeriodicSyncNotificationWork(
        appContext: Context,
        frequencyInDays: Long,
        policy: ExistingPeriodicWorkPolicy
    ) {
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            WORKER_TAG_SYNC_NOTIFICATION,
            policy,
            SyncNotificationWorker.newWorkRequest(frequencyInDays)
        )
    }
}
