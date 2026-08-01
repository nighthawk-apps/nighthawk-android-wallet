package com.nighthawkapps.lib.android.global

import android.annotation.SuppressLint
import android.os.Environment
import com.nighthawkapps.lib.android.ui.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object StorageChecker {
    const val REQUIRED_FREE_SPACE_MEGABYTES: Int = 1000

    /** Debug builds use a lower bar so emulators with ~600MB free can reach onboarding. */
    fun requiredFreeSpaceMegabytes(): Int =
        if (BuildConfig.DEBUG) {
            500
        } else {
            REQUIRED_FREE_SPACE_MEGABYTES
        }

    suspend fun isEnoughSpace() = checkAvailableStorageMegabytes() > requiredFreeSpaceMegabytes()

    @SuppressLint("UsableSpace")
    @Suppress("MagicNumber")
    suspend fun checkAvailableStorageMegabytes(): Int =
        withContext(Dispatchers.IO) {
            return@withContext (Environment.getDataDirectory().usableSpace / (1024 * 1024)).toInt()
        }

    suspend fun spaceRequiredToContinueMegabytes() =
        withContext(Dispatchers.IO) {
            return@withContext requiredFreeSpaceMegabytes() - checkAvailableStorageMegabytes()
        }
}
