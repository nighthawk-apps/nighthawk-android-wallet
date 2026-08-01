package com.nighthawkapps.lib.android.preference.model.entry

import com.nighthawkapps.lib.android.preference.api.PreferenceProvider

data class IntegerPreferenceDefault(
    override val key: PreferenceKey,
    private val defaultValue: Int
) : PreferenceDefault<Int> {
    override suspend fun getValue(preferenceProvider: PreferenceProvider) =
        preferenceProvider.getString(key)?.let {
            try {
                it.toInt()
            } catch (e: NumberFormatException) {
                logPreferenceCoercionFailure("integer", key, it, e)
                defaultValue
            }
        } ?: defaultValue

    override suspend fun putValue(
        preferenceProvider: PreferenceProvider,
        newValue: Int
    ) {
        preferenceProvider.putString(key, newValue.toString())
    }
}
