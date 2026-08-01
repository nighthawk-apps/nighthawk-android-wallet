package com.nighthawkapps.lib.android.ui.screen.security.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nighthawkapps.lib.android.ui.common.ANDROID_STATE_FLOW_TIMEOUT
import com.nighthawkapps.lib.android.ui.common.isBioMetricEnabledOnMobile
import com.nighthawkapps.lib.android.ui.preference.StandardPreferenceKeys
import com.nighthawkapps.lib.android.ui.preference.StandardPreferenceSingleton
import com.nighthawkapps.lib.android.ui.security.WalletPinSecureStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SecurityViewModel(
    val context: Application,
) : AndroidViewModel(application = context) {
    val isPinEnabled =
        flow {
            emit(WalletPinSecureStore.isConfigured(context))
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT.inWholeMilliseconds),
            null,
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val isTouchIdOrFaceIdEnabled =
        flow {
            val preferenceProvider = StandardPreferenceSingleton.getInstance(context)
            emit(StandardPreferenceKeys.IS_TOUCH_ID_OR_FACE_ID_ENABLED.observe(preferenceProvider))
        }.flatMapLatest { it }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT.inWholeMilliseconds),
            null,
        )

    fun updateTouchIdFaceIdStatus(enable: Boolean) {
        viewModelScope.launch {
            val preferenceProvider = StandardPreferenceSingleton.getInstance(context)
            StandardPreferenceKeys.IS_TOUCH_ID_OR_FACE_ID_ENABLED.putValue(preferenceProvider, enable)
        }
    }

    fun disablePin() {
        viewModelScope.launch(Dispatchers.IO) {
            WalletPinSecureStore.clear(context)
        }
    }

    fun isBioMetricEnabledOnMobile() = context.isBioMetricEnabledOnMobile()
}
