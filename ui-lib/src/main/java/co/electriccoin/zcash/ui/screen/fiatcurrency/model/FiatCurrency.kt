package co.electriccoin.zcash.ui.screen.fiatcurrency.model

/**
 * @param currencyName is the currency representation
 * @param currencyText is used to show the Text in UI
 * @param serverUrl is used to hit the coinMetrics api to get the zec value
 */
enum class FiatCurrency(val currencyName: String, val currencyText: String, val serverUrl: String) {
    USD("USD", "United States Dollar", "usd"),
    EUR("EUR", "Euro", "eur"),
    INR("INR", "Indian Rupee", "inr"),
    JPY("JPY", "Japanese Yen", "jpy"),
    GBP("GBP", "British Pound", "gbp"),
    CAD("CAD", "Canadian Dollar", "cad"),
    AUD("AUD", "Australian Dollar", "aud"),
    HKD("HKD", "Hong Kong Dollar", "hkd"),
    SGD("SGD", "Singapore Dollar", "sgd"),
    CHF("CHF", "Swiss Franc", "chf"),
    CNY("CNY", "Chinese Yuan", "cny"),
    KRW("KRW", "Korean Won", "krw"),
    OFF("", "OFF", "");

    companion object {
        fun getFiatCurrencyByName(currencyName: String): FiatCurrency {
            return when (currencyName.lowercase()) {
                USD.currencyName.lowercase() -> USD
                EUR.currencyName.lowercase() -> EUR
                INR.currencyName.lowercase() -> INR
                JPY.currencyName.lowercase() -> JPY
                GBP.currencyName.lowercase() -> GBP
                CAD.currencyName.lowercase() -> CAD
                AUD.currencyName.lowercase() -> AUD
                HKD.currencyName.lowercase() -> HKD
                SGD.currencyName.lowercase() -> SGD
                CHF.currencyName.lowercase() -> CHF
                CNY.currencyName.lowercase() -> CNY
                KRW.currencyName.lowercase() -> KRW
                else -> OFF
            }
        }
        fun getFiatCurrencyByServerUrl(serverUrl: String): FiatCurrency {
            return when (serverUrl.lowercase()) {
                USD.serverUrl.lowercase() -> USD
                EUR.serverUrl.lowercase() -> EUR
                INR.serverUrl.lowercase() -> INR
                JPY.serverUrl.lowercase() -> JPY
                GBP.serverUrl.lowercase() -> GBP
                CAD.serverUrl.lowercase() -> CAD
                AUD.serverUrl.lowercase() -> AUD
                HKD.serverUrl.lowercase() -> HKD
                SGD.serverUrl.lowercase() -> SGD
                CHF.serverUrl.lowercase() -> CHF
                CNY.serverUrl.lowercase() -> CNY
                KRW.serverUrl.lowercase() -> KRW
                else -> OFF
            }
        }
    }
}

data class FiatCurrencyUiState(val fiatCurrency: FiatCurrency, val price: Double?)
