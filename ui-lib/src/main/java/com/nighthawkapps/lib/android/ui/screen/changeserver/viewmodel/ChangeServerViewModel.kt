package com.nighthawkapps.lib.android.ui.screen.changeserver.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nighthawkapps.lib.android.sdk.wallet.darkfiNetworkFromPackage
import com.nighthawkapps.lib.android.spackle.Twig
import com.nighthawkapps.lib.android.ui.common.ANDROID_STATE_FLOW_TIMEOUT
import com.nighthawkapps.lib.android.ui.preference.EncryptedPreferenceKeys
import com.nighthawkapps.lib.android.ui.preference.EncryptedPreferenceSingleton
import com.nighthawkapps.lib.android.ui.screen.changeserver.model.DarkfiEndpointCatalog
import com.nighthawkapps.lib.android.ui.screen.changeserver.model.DarkfiEndpointPreset
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChangeServerViewModel(
    private val application: Application
) : AndroidViewModel(application = application) {
    val selectedPreset =
        flow {
            val pref = EncryptedPreferenceSingleton.getInstance(application)
            val persisted =
                EncryptedPreferenceKeys.DARKFI_ENDPOINT_PRESET.getValue(pref)
                    ?: DarkfiEndpointCatalog.defaultPreset(darkfiNetworkFromPackage(application))
            Twig.info { "Saved DarkFi endpoint preset: $persisted" }
            emit(persisted)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT.inWholeMilliseconds),
            null,
        )

    fun presetOptions(): ImmutableList<DarkfiEndpointPreset> = DarkfiEndpointCatalog.presets(darkfiNetworkFromPackage(application))

    fun updateSelectedPreset(preset: DarkfiEndpointPreset?) {
        viewModelScope.launch(Dispatchers.IO) {
            val pref = EncryptedPreferenceSingleton.getInstance(application)
            Twig.info { "Persist DarkFi endpoint preset: $preset" }
            EncryptedPreferenceKeys.DARKFI_ENDPOINT_PRESET.putValue(pref, preset)
        }
    }
}
