package co.electriccoin.zcash.ui.screen.fiatcurrency.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.common.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.preference.EncryptedPreferenceKeys
import co.electriccoin.zcash.ui.preference.EncryptedPreferenceSingleton
import co.electriccoin.zcash.ui.screen.fiatcurrency.model.FiatCurrency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FiatCurrencyViewModel(val context: Application): AndroidViewModel(application = context) {

    val preferredFiatCurrency = flow {
        val pref = EncryptedPreferenceSingleton.getInstance(context)
        emitAll(
            EncryptedPreferenceKeys.PREFERRED_FIAT_CURRENCY_NAME.observe(pref).map {
                FiatCurrency.getFiatCurrencyByName(it)
            }
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
        FiatCurrency.OFF
    )

    fun updateFiatCurrency(fiatCurrency: FiatCurrency) {
        viewModelScope.launch(Dispatchers.IO) {
            val pref = EncryptedPreferenceSingleton.getInstance(context)
            EncryptedPreferenceKeys.PREFERRED_FIAT_CURRENCY_NAME.putValue(pref, fiatCurrency.currencyName)
            if (fiatCurrency == FiatCurrency.OFF) {
                resetCurrencySavedValue()
            }
        }
    }

    private fun resetCurrencySavedValue() {
        val application = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val preference = EncryptedPreferenceSingleton.getInstance(application)
            EncryptedPreferenceKeys.PREFERRED_FIAT_CURRENCY_VALUE.putValue(preference, "0.0")
        }
    }

}
