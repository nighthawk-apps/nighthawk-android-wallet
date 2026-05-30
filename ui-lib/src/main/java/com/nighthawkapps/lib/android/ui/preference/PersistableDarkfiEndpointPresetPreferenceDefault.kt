package com.nighthawkapps.lib.android.ui.preference

import com.nighthawkapps.lib.android.preference.api.PreferenceProvider
import com.nighthawkapps.lib.android.preference.model.entry.PreferenceDefault
import com.nighthawkapps.lib.android.preference.model.entry.PreferenceKey
import com.nighthawkapps.lib.android.ui.screen.changeserver.model.DarkfiEndpointPreset
import org.json.JSONObject

data class PersistableDarkfiEndpointPresetPreferenceDefault(
    override val key: PreferenceKey,
) : PreferenceDefault<DarkfiEndpointPreset?> {
    override suspend fun getValue(preferenceProvider: PreferenceProvider) =
        preferenceProvider.getString(key)?.let { DarkfiEndpointPreset.fromJson(JSONObject(it)) }

    override suspend fun putValue(
        preferenceProvider: PreferenceProvider,
        newValue: DarkfiEndpointPreset?,
    ) = preferenceProvider.putString(key, newValue?.toJson()?.toString())
}
