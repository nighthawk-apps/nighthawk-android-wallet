package co.electriccoin.zcash.ui.preference

import co.electriccoin.zcash.preference.model.entry.PreferenceKey
import co.electriccoin.zcash.preference.model.entry.StringPreferenceDefault

object EncryptedPreferenceKeys {

    val PERSISTABLE_WALLET = PersistableWalletPreferenceDefault(PreferenceKey("persistable_wallet"))

    val SYNC_INTERVAL_OPTION = SyncIntervalOptionPreferenceDefault(PreferenceKey("sync_interval_option"))

    val PREFERRED_FIAT_CURRENCY_NAME = StringPreferenceDefault(PreferenceKey("preferred_fiat_currency_name"), "")

    val PREFERRED_FIAT_CURRENCY_VALUE = StringPreferenceDefault(PreferenceKey("preferred_fiat_currency_value"), "0.0")
}
