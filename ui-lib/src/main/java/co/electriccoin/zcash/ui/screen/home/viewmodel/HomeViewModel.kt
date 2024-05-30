package co.electriccoin.zcash.ui.screen.home.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.configuration.AndroidConfigurationFactory
import co.electriccoin.zcash.configuration.model.map.Configuration
import co.electriccoin.zcash.global.DeepLinkUtil
import co.electriccoin.zcash.network.repository.CoinMetricsRepositoryImpl
import co.electriccoin.zcash.network.util.Resource
import co.electriccoin.zcash.network.util.RetrofitHelper
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.common.ShortcutAction
import co.electriccoin.zcash.ui.preference.EncryptedPreferenceKeys
import co.electriccoin.zcash.ui.preference.EncryptedPreferenceSingleton
import co.electriccoin.zcash.ui.preference.StandardPreferenceKeys
import co.electriccoin.zcash.ui.preference.StandardPreferenceSingleton
import co.electriccoin.zcash.ui.screen.fiatcurrency.model.FiatCurrency
import co.electriccoin.zcash.ui.screen.fiatcurrency.model.FiatCurrencyUiState
import co.electriccoin.zcash.ui.screen.home.model.WalletSnapshot
import co.electriccoin.zcash.ui.screen.home.model.spendableBalance
import co.electriccoin.zcash.ui.screen.home.model.totalBalance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    /**
     * A flow of whether background sync is enabled
     */
    val isBackgroundSyncEnabled: StateFlow<Boolean?> = flow {
        val preferenceProvider = StandardPreferenceSingleton.getInstance(application)
        emitAll(StandardPreferenceKeys.IS_BACKGROUND_SYNC_ENABLED.observe(preferenceProvider))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT.inWholeMilliseconds), null)

    val configurationFlow: StateFlow<Configuration?> =
        AndroidConfigurationFactory.getInstance(application).getConfigurationFlow()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT.inWholeMilliseconds),
                null
            )

    val isDarkThemeEnabled = flow {
        val preferenceProvider = StandardPreferenceSingleton.getInstance(application)
        emitAll(StandardPreferenceKeys.IS_DARK_THEME_ENABLED.observe(preferenceProvider))
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT.inWholeMilliseconds),
        false
    )

    var intentDataUriForDeepLink: Uri? = null
    var sendDeepLinkData: DeepLinkUtil.SendDeepLinkData? = null
    var shortcutAction: ShortcutAction? = null
    // Flag to track any expecting balance is there or not. We will show snackBar everytime user open the app until it is a confirmed transaction
    var expectingZatoshi = 0L

    fun isAnyExpectingTransaction(walletSnapshot: WalletSnapshot): Boolean {
        val totalBalance = walletSnapshot.totalBalance()
        val availableBalance = walletSnapshot.spendableBalance()
        if (totalBalance != availableBalance && ((totalBalance - availableBalance).value != expectingZatoshi)) {
            expectingZatoshi = (totalBalance - availableBalance).value
            return true
        }
        return false
    }

    fun getZecPriceFromCoinMetrics(currencyServerUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                CoinMetricsRepositoryImpl(RetrofitHelper.getCoinMetricsApiService())
                    .getZecMarketData(currencyServerUrl)
                    .catch { Twig.error { "Exception in getting price from coin metrics catch $it" } }
                    .collectLatest {
                        when (it) {
                            is Resource.Success -> {
                                val value = it.response.data.values.firstOrNull()
                                saveFiatCurrencyValue(value)
                                Twig.debug { "Price fetched $value" }
                                _fiatCurrencyUiStateFlow.update { fiatCurrencyUiState ->
                                    fiatCurrencyUiState.copy(
                                        fiatCurrency = FiatCurrency.getFiatCurrencyByServerUrl(currencyServerUrl),
                                        price = value
                                    )
                                }
                            }
                            else -> {
                                Twig.debug { "Getting price state $it" }
                            }
                        }
                    }
            } catch (e: Exception) {
                Twig.error { "Exception in getting price from coin metrics $e" }
            }
        }
    }

    private fun saveFiatCurrencyValue(value: Double?) {
        val application = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val preference = EncryptedPreferenceSingleton.getInstance(application)
            EncryptedPreferenceKeys.PREFERRED_FIAT_CURRENCY_VALUE.putValue(preference, (value ?: 0.0).toString())
        }
    }

    /**
     * Get Zec price for FiatCurrency. This will called only when we don't have price fetched yet or currency selection is changed
     */
    fun fetchZecPriceFromCoinMetrics() {
        val application = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val preference = EncryptedPreferenceSingleton.getInstance(application)
            EncryptedPreferenceKeys.PREFERRED_FIAT_CURRENCY_NAME.getValue(preference).let {
                val fiatCurrency = FiatCurrency.getFiatCurrencyByName(it)
                if (fiatCurrency != _fiatCurrencyUiStateFlow.value.fiatCurrency) {
                    getZecPriceFromCoinMetrics(fiatCurrency.serverUrl)
                }
            }
        }
    }

    val isFiatCurrencyPreferredOverZec = flow {
        val preference = EncryptedPreferenceSingleton.getInstance(application)
        emitAll(EncryptedPreferenceKeys.IS_FIAT_CURRENCY_PREFERRED.observe(preference))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT.inWholeMilliseconds), false)

    fun onPreferredCurrencyChanged(isFiatCurrencyPreferredOverZec: Boolean) {
        val application = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val preference = EncryptedPreferenceSingleton.getInstance(application)
            EncryptedPreferenceKeys.IS_FIAT_CURRENCY_PREFERRED.putValue(preference, isFiatCurrencyPreferredOverZec)
        }
    }

    private val _fiatCurrencyUiStateFlow = MutableStateFlow(FiatCurrencyUiState(FiatCurrency.OFF, null))
    val fiatCurrencyUiStateFlow get() = _fiatCurrencyUiStateFlow.asStateFlow()
}
