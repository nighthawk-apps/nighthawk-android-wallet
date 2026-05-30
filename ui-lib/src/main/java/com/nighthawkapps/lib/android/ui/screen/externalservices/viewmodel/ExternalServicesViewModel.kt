package com.nighthawkapps.lib.android.ui.screen.externalservices.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nighthawkapps.lib.android.ui.common.ANDROID_STATE_FLOW_TIMEOUT
import com.nighthawkapps.lib.android.ui.preference.StandardPreferenceKeys
import com.nighthawkapps.lib.android.ui.preference.StandardPreferenceSingleton
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExternalServicesViewModel(
    val context: Application
) : AndroidViewModel(application = context) {
    val isUnStoppableServiceEnabled =
        flow {
            val pref = StandardPreferenceSingleton.getInstance(context)
            emitAll(StandardPreferenceKeys.IS_UNSTOPPABLE_SERVICE_ENABLED.observe(pref))
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT.inWholeMilliseconds),
            null
        )

    fun updateUnStoppableServiceStatus(isEnabled: Boolean) {
        viewModelScope.launch {
            val pref = StandardPreferenceSingleton.getInstance(context)
            StandardPreferenceKeys.IS_UNSTOPPABLE_SERVICE_ENABLED.putValue(pref, isEnabled)
        }
    }
}
