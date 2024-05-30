package co.electriccoin.zcash.ui.preference

import co.electriccoin.zcash.preference.api.PreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceDefault
import co.electriccoin.zcash.preference.model.entry.PreferenceKey
import co.electriccoin.zcash.ui.screen.changeserver.model.LightWalletServer
import org.json.JSONObject

data class PersistableLightWalletServerPreferenceDefault(
    override val key: PreferenceKey
) : PreferenceDefault<LightWalletServer?> {

    override suspend fun getValue(preferenceProvider: PreferenceProvider) =
        preferenceProvider.getString(key)?.let { LightWalletServer.from(JSONObject(it)) }

    override suspend fun putValue(
        preferenceProvider: PreferenceProvider,
        newValue: LightWalletServer?
    ) = preferenceProvider.putString(key, newValue?.toJson()?.toString())
}
