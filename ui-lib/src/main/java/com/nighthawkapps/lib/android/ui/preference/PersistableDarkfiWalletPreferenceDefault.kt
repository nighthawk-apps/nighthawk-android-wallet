package com.nighthawkapps.lib.android.ui.preference

import com.nighthawkapps.lib.android.preference.api.PreferenceProvider
import com.nighthawkapps.lib.android.preference.model.entry.PreferenceDefault
import com.nighthawkapps.lib.android.preference.model.entry.PreferenceKey
import com.nighthawkapps.lib.android.sdk.wallet.PersistableDarkfiWallet
import org.json.JSONObject

data class PersistableDarkfiWalletPreferenceDefault(
    override val key: PreferenceKey,
) : PreferenceDefault<PersistableDarkfiWallet?> {
    override suspend fun getValue(preferenceProvider: PreferenceProvider) =
        preferenceProvider.getString(key)?.let { PersistableDarkfiWallet.fromJsonObject(JSONObject(it)) }

    override suspend fun putValue(
        preferenceProvider: PreferenceProvider,
        newValue: PersistableDarkfiWallet?,
    ) = preferenceProvider.putString(key, newValue?.let { PersistableDarkfiWallet.toJsonObject(it).toString() })
}
