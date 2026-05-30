package com.nighthawkapps.lib.android.ui.screen.support.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nighthawkapps.lib.android.ui.common.ANDROID_STATE_FLOW_TIMEOUT
import com.nighthawkapps.lib.android.ui.screen.support.model.SupportInfo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

class SupportViewModel(
    application: Application
) : AndroidViewModel(application) {
    // Technically, some of the support info could be invalidated after a configuration change,
    // such as the user's current locale. However it really doesn't matter here since all we
    // care about is capturing a snapshot of the app, OS, and device state.
    val supportInfo: StateFlow<SupportInfo?> =
        flow<SupportInfo?> {
            emit(SupportInfo.new(application))
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT.inWholeMilliseconds, replayExpirationMillis = 0L),
            null
        )
}
