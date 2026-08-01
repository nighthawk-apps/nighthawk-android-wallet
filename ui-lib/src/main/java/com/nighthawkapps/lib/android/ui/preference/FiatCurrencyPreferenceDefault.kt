package com.nighthawkapps.lib.android.ui.preference

import com.nighthawkapps.lib.android.preference.api.PreferenceProvider
import com.nighthawkapps.lib.android.preference.model.entry.PreferenceDefault
import com.nighthawkapps.lib.android.preference.model.entry.PreferenceKey
import com.nighthawkapps.lib.android.ui.screen.fiatcurrency.model.FiatCurrency

data class FiatCurrencyPreferenceDefault(
    override val key: PreferenceKey
) : PreferenceDefault<FiatCurrency> {
    override suspend fun getValue(preferenceProvider: PreferenceProvider) =
        preferenceProvider.getString(key)?.let { FiatCurrency.getFiatCurrencyByName(it) }
            ?: FiatCurrency.OFF

    override suspend fun putValue(
        preferenceProvider: PreferenceProvider,
        newValue: FiatCurrency,
    ) = preferenceProvider.putString(key, newValue.currencyName)
}
