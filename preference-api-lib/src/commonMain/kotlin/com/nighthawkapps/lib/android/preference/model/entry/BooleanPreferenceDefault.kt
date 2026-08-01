package com.nighthawkapps.lib.android.preference.model.entry

import com.nighthawkapps.lib.android.preference.api.PreferenceProvider

data class BooleanPreferenceDefault(
    override val key: PreferenceKey,
    private val defaultValue: Boolean
) : PreferenceDefault<Boolean> {
    override suspend fun getValue(preferenceProvider: PreferenceProvider) =
        preferenceProvider.getString(key)?.let {
            try {
                it.toBooleanStrict()
            } catch (e: IllegalArgumentException) {
                logPreferenceCoercionFailure("boolean", key, it, e)
                defaultValue
            }
        } ?: defaultValue

    override suspend fun putValue(
        preferenceProvider: PreferenceProvider,
        newValue: Boolean
    ) {
        preferenceProvider.putString(key, newValue.toString())
    }
}
