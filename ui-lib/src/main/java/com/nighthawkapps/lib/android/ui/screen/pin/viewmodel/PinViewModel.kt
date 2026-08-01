package com.nighthawkapps.lib.android.ui.screen.pin.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nighthawkapps.lib.android.ui.common.ANDROID_STATE_FLOW_TIMEOUT
import com.nighthawkapps.lib.android.ui.common.isBioMetricEnabledOnMobile
import com.nighthawkapps.lib.android.ui.preference.StandardPreferenceKeys
import com.nighthawkapps.lib.android.ui.preference.StandardPreferenceSingleton
import com.nighthawkapps.lib.android.ui.security.WalletPinSecureStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PinViewModel(
    val context: Application,
) : AndroidViewModel(application = context) {
    fun savePin(pin: String) {
        viewModelScope.launch(Dispatchers.IO) {
            WalletPinSecureStore.setPin(context, pin)
        }
    }

    suspend fun verifyPin(pin: String): WalletPinSecureStore.PinVerifyResult =
        withContext(Dispatchers.IO) {
            WalletPinSecureStore.verify(context, pin)
        }

    fun lockoutRemainingSeconds(): Long = WalletPinSecureStore.lockoutRemainingSeconds(context)

    val isPinConfigured =
        flow {
            emit(WalletPinSecureStore.isConfigured(context))
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT.inWholeMilliseconds),
            null,
        )

    val isTouchIdOrFaceIdEnabled =
        flow {
            val preferenceProvider = StandardPreferenceSingleton.getInstance(context)
            emit(StandardPreferenceKeys.IS_TOUCH_ID_OR_FACE_ID_ENABLED.getValue(preferenceProvider))
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT.inWholeMilliseconds),
            null,
        )

    fun isBioMetricEnabledOnMobile() = context.isBioMetricEnabledOnMobile()
}
