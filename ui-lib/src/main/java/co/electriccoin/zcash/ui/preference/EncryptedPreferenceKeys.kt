package co.electriccoin.zcash.ui.preference

import co.electriccoin.zcash.preference.model.entry.BooleanPreferenceDefault
import co.electriccoin.zcash.preference.model.entry.PreferenceKey
import co.electriccoin.zcash.preference.model.entry.StringPreferenceDefault

object EncryptedPreferenceKeys {

    val PERSISTABLE_WALLET = PersistableWalletPreferenceDefault(PreferenceKey("persistable_wallet"))

    val SYNC_INTERVAL_OPTION = SyncIntervalOptionPreferenceDefault(PreferenceKey("sync_interval_option"))

    val PREFERRED_FIAT_CURRENCY_NAME = StringPreferenceDefault(PreferenceKey("preferred_fiat_currency_name"), "")

    val PREFERRED_FIAT_CURRENCY_VALUE = StringPreferenceDefault(PreferenceKey("preferred_fiat_currency_value"), "0.0")

    val IS_FIAT_CURRENCY_PREFERRED = BooleanPreferenceDefault(PreferenceKey("is_fiat_currency_preferred_over_zec"), false)

    val SELECTED_SERVER = StringPreferenceDefault(PreferenceKey("selected_server"), "default")

    val LIGHT_WALLET_SERVER = PersistableLightWalletServerPreferenceDefault(PreferenceKey("persistable_light_wallet_Server"))
}
