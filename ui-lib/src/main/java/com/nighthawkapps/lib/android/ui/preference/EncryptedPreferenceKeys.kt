package com.nighthawkapps.lib.android.ui.preference

import com.nighthawkapps.lib.android.preference.model.entry.BooleanPreferenceDefault
import com.nighthawkapps.lib.android.preference.model.entry.PreferenceKey
import com.nighthawkapps.lib.android.preference.model.entry.StringPreferenceDefault

object EncryptedPreferenceKeys {
    val PERSISTABLE_DARKFI_WALLET =
        PersistableDarkfiWalletPreferenceDefault(PreferenceKey("persistable_darkfi_wallet"))

    val SYNC_INTERVAL_OPTION = SyncIntervalOptionPreferenceDefault(PreferenceKey("sync_interval_option"))

    val PREFERRED_FIAT_CURRENCY_NAME = StringPreferenceDefault(PreferenceKey("preferred_fiat_currency_name"), "")

    val PREFERRED_FIAT_CURRENCY_VALUE = StringPreferenceDefault(PreferenceKey("preferred_fiat_currency_value"), "0.0")

    val IS_FIAT_CURRENCY_PREFERRED = BooleanPreferenceDefault(PreferenceKey("is_fiat_currency_preferred_over_drk"), false)

    val SELECTED_SERVER = StringPreferenceDefault(PreferenceKey("selected_server"), "default")

    val DARKFI_ENDPOINT_PRESET =
        PersistableDarkfiEndpointPresetPreferenceDefault(PreferenceKey("persistable_darkfi_endpoint_preset"))
}
