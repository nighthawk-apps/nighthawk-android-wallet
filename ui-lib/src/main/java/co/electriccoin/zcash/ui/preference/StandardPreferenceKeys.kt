package co.electriccoin.zcash.ui.preference

import co.electriccoin.zcash.preference.model.entry.BooleanPreferenceDefault
import co.electriccoin.zcash.preference.model.entry.PreferenceKey
import co.electriccoin.zcash.preference.model.entry.StringPreferenceDefault

object StandardPreferenceKeys {

    /**
     * Whether the user has completed the backup flow for a newly created wallet.
     */
    val IS_USER_BACKUP_COMPLETE = BooleanPreferenceDefault(PreferenceKey("is_user_backup_complete"), false)

    // Default to true until https://github.com/zcash/secant-android-wallet/issues/304
    val IS_ANALYTICS_ENABLED = BooleanPreferenceDefault(PreferenceKey("is_analytics_enabled"), true)

    val IS_BACKGROUND_SYNC_ENABLED = BooleanPreferenceDefault(PreferenceKey("is_background_sync_enabled"), true)

    val IS_KEEP_SCREEN_ON_DURING_SYNC = BooleanPreferenceDefault(PreferenceKey("is_keep_screen_on_during_sync"), true)

    val IS_AUTOSHIELDING_INFO_ACKNOWLEDGED = BooleanPreferenceDefault(PreferenceKey("is_autoshielding_info_acknowledged"), false)

    val LAST_AUTOSHIELDING_PROMPT_EPOCH_MILLIS_STRING = StringPreferenceDefault(PreferenceKey("last_autoshielding_epoch_millis"), "0")

    /**
     * The fiat currency that the user prefers.
     */
    val PREFERRED_FIAT_CURRENCY = FiatCurrencyPreferenceDefault(PreferenceKey("preferred_fiat_currency_code"))
}
