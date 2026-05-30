package com.nighthawkapps.lib.android.ui.screen.warning.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nighthawkapps.lib.android.global.StorageChecker
import com.nighthawkapps.lib.android.ui.common.ANDROID_STATE_FLOW_TIMEOUT
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

class StorageCheckViewModel : ViewModel() {
    @Suppress("MagicNumber")
    val requiredStorageSpaceGigabytes: Int =
        (StorageChecker.REQUIRED_FREE_SPACE_MEGABYTES / 1000)

    val isEnoughSpace =
        flow { emit(StorageChecker.isEnoughSpace()) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT.inWholeMilliseconds),
                null
            )

    val spaceRequiredToContinueMegabytes =
        flow { emit(StorageChecker.spaceRequiredToContinueMegabytes()) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT.inWholeMilliseconds),
                null
            )
}
