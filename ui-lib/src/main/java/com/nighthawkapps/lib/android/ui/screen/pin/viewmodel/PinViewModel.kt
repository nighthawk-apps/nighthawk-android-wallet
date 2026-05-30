package com.nighthawkapps.lib.android.ui.screen.pin.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nighthawkapps.lib.android.ui.common.ANDROID_STATE_FLOW_TIMEOUT
import com.nighthawkapps.lib.android.ui.common.isBioMetricEnabledOnMobile
import com.nighthawkapps.lib.android.ui.preference.StandardPreferenceKeys
import com.nighthawkapps.lib.android.ui.preference.StandardPreferenceSingleton
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PinViewModel(
    val context: Application
) : AndroidViewModel(application = context) {
    fun savePin(pin: String) {
        viewModelScope.launch {
            val preferenceProvider = StandardPreferenceSingleton.getInstance(context)
            StandardPreferenceKeys.LAST_ENTERED_PIN.putValue(preferenceProvider, pin)
        }
    }

    val lastSavedPin =
        flow {
            val preferenceProvider = StandardPreferenceSingleton.getInstance(context)
            emit(StandardPreferenceKeys.LAST_ENTERED_PIN.getValue(preferenceProvider))
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT.inWholeMilliseconds),
            null
        )

    val isTouchIdOrFaceIdEnabled =
        flow {
            val preferenceProvider = StandardPreferenceSingleton.getInstance(context)
            emit(StandardPreferenceKeys.IS_TOUCH_ID_OR_FACE_ID_ENABLED.getValue(preferenceProvider))
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT.inWholeMilliseconds),
            null
        )

    fun isBioMetricEnabledOnMobile() = context.isBioMetricEnabledOnMobile()
}
