package com.nighthawkapps.lib.android.ui.screen.home.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nighthawkapps.lib.android.configuration.AndroidConfigurationFactory
import com.nighthawkapps.lib.android.configuration.model.map.Configuration
import com.nighthawkapps.lib.android.global.DeepLinkUtil
import com.nighthawkapps.lib.android.network.repository.CoinMetricsRepositoryImpl
import com.nighthawkapps.lib.android.network.util.Resource
import com.nighthawkapps.lib.android.network.util.RetrofitHelper
import com.nighthawkapps.lib.android.spackle.Twig
import com.nighthawkapps.lib.android.ui.common.ANDROID_STATE_FLOW_TIMEOUT
import com.nighthawkapps.lib.android.ui.common.ShortcutAction
import com.nighthawkapps.lib.android.ui.design.theme.AppThemeVariant
import com.nighthawkapps.lib.android.ui.preference.EncryptedPreferenceKeys
import com.nighthawkapps.lib.android.ui.preference.EncryptedPreferenceSingleton
import com.nighthawkapps.lib.android.ui.preference.StandardPreferenceKeys
import com.nighthawkapps.lib.android.ui.preference.StandardPreferenceSingleton
import com.nighthawkapps.lib.android.ui.screen.fiatcurrency.model.FiatCurrency
import com.nighthawkapps.lib.android.ui.screen.fiatcurrency.model.FiatCurrencyUiState
import com.nighthawkapps.lib.android.ui.screen.home.model.WalletSnapshot
import com.nighthawkapps.lib.android.ui.screen.home.model.spendableBalanceAtomic
import com.nighthawkapps.lib.android.ui.screen.home.model.totalBalanceAtomic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    application: Application
) : AndroidViewModel(application) {
    /**
     * A flow of whether background sync is enabled
     */
    val isBackgroundSyncEnabled: StateFlow<Boolean?> =
        flow {
            val preferenceProvider = StandardPreferenceSingleton.getInstance(application)
            emitAll(StandardPreferenceKeys.IS_BACKGROUND_SYNC_ENABLED.observe(preferenceProvider))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT.inWholeMilliseconds), null)

    val configurationFlow: StateFlow<Configuration?> =
        AndroidConfigurationFactory
            .getInstance(application)
            .getConfigurationFlow()
            .stateIn(
                viewModelScope,
                // Eagerly: splash keep-on-screen reads .value before Compose subscribes.
                SharingStarted.Eagerly,
                null,
            )

    val appThemeVariant: StateFlow<AppThemeVariant> =
        flow {
            val prefs = StandardPreferenceSingleton.getInstance(application)
            emitAll(
                combine(
                    StandardPreferenceKeys.APP_THEME_VARIANT.observe(prefs),
                    StandardPreferenceKeys.IS_DARK_THEME_ENABLED.observe(prefs),
                ) { stored, legacyDark ->
                    AppThemeVariant.resolve(stored, legacyDark)
                },
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT.inWholeMilliseconds),
            AppThemeVariant.STEALTH_DEFAULT,
        )

    var intentDataUriForDeepLink: Uri? = null
    var sendDeepLinkData: DeepLinkUtil.SendDeepLinkData? = null
    var shortcutAction: ShortcutAction? = null

    // Flag to track any expecting balance is there or not. We will show snackBar everytime user open the app until it is a confirmed transaction
    var expectingPendingAtomic = 0L

    fun isAnyExpectingTransaction(walletSnapshot: WalletSnapshot): Boolean {
        val totalBalance = walletSnapshot.totalBalanceAtomic()
        val availableBalance = walletSnapshot.spendableBalanceAtomic()
        if (totalBalance != availableBalance && ((totalBalance - availableBalance) != expectingPendingAtomic)) {
            expectingPendingAtomic = totalBalance - availableBalance
            return true
        }
        return false
    }

    fun refreshSpotPrice(currencyServerUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                CoinMetricsRepositoryImpl(RetrofitHelper.getCoinMetricsApiService(getApplication()))
                    .observeSpotPrice(currencyServerUrl)
                    .catch { Twig.error { "Exception in getting price from coin metrics catch $it" } }
                    .collectLatest {
                        when (it) {
                            is Resource.Success -> {
                                val value =
                                    it.response.vsPrices
                                        ?.values
                                        ?.firstOrNull()
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
     * DRK spot quote for the selected fiat pairing (CoinGecko simple/price).
     */
    fun scheduleSpotPriceRefresh() {
        val application = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val preference = EncryptedPreferenceSingleton.getInstance(application)
            EncryptedPreferenceKeys.PREFERRED_FIAT_CURRENCY_NAME.getValue(preference).let {
                val fiatCurrency = FiatCurrency.getFiatCurrencyByName(it)
                if (fiatCurrency != _fiatCurrencyUiStateFlow.value.fiatCurrency) {
                    refreshSpotPrice(fiatCurrency.serverUrl)
                }
            }
        }
    }

    val isFiatCurrencyPreferredOverNative =
        flow {
            val preference = EncryptedPreferenceSingleton.getInstance(application)
            emitAll(EncryptedPreferenceKeys.IS_FIAT_CURRENCY_PREFERRED.observe(preference))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT.inWholeMilliseconds), false)

    fun onPreferredCurrencyChanged(isFiatCurrencyPreferredOverNative: Boolean) {
        val application = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val preference = EncryptedPreferenceSingleton.getInstance(application)
            EncryptedPreferenceKeys.IS_FIAT_CURRENCY_PREFERRED.putValue(preference, isFiatCurrencyPreferredOverNative)
        }
    }

    private val _fiatCurrencyUiStateFlow = MutableStateFlow(FiatCurrencyUiState(FiatCurrency.OFF, null))
    val fiatCurrencyUiStateFlow get() = _fiatCurrencyUiStateFlow.asStateFlow()
}
