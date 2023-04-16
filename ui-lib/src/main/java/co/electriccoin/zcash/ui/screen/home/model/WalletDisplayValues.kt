package co.electriccoin.zcash.ui.screen.home.model

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.ui.text.intl.Locale
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.model.FiatCurrencyConversionRateState
import cash.z.ecc.android.sdk.model.MonetarySeparators
import cash.z.ecc.android.sdk.model.PercentDecimal
import cash.z.ecc.android.sdk.model.toFiatCurrencyState
import cash.z.ecc.android.sdk.model.toZecString
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.toKotlinLocale
import kotlin.math.roundToInt

data class WalletDisplayValues(
    val progress: PercentDecimal,
    val zecAmountText: String,
    val statusText: String,
    val fiatCurrencyAmountState: FiatCurrencyConversionRateState,
    val fiatCurrencyAmountText: String,
    @DrawableRes val statusIconDrawable: Int
) {
    companion object {
        @Suppress("MagicNumber", "LongMethod")
        internal fun getNextValues(
            context: Context,
            walletSnapshot: WalletSnapshot,
            updateAvailable: Boolean
        ): WalletDisplayValues {
            var progress = PercentDecimal.ZERO_PERCENT
            val zecAmountText = walletSnapshot.totalBalance().toZecString()
            var statusText = context.getString(R.string.ns_connecting)
            // TODO [#578] https://github.com/zcash/zcash-android-wallet-sdk/issues/578
            // We'll ideally provide a "fresh" currencyConversion object here
            val fiatCurrencyAmountState = walletSnapshot.spendableBalance().toFiatCurrencyState(
                null,
                Locale.current.toKotlinLocale(),
                MonetarySeparators.current()
            )
            var fiatCurrencyAmountText = getFiatCurrencyRateValue(context, fiatCurrencyAmountState)
            var statusIconDrawable = R.drawable.ic_icon_connecting

            when (walletSnapshot.status) {
                Synchronizer.Status.PREPARING -> {
                    statusText = context.getString(R.string.ns_preparing_scan)
                    statusIconDrawable = R.drawable.ic_icon_connecting
                }
                Synchronizer.Status.DOWNLOADING -> {
                    progress = walletSnapshot.progress
                    statusIconDrawable = R.drawable.ic_icon_downloading
                    val progressPercent = (walletSnapshot.progress.decimal * 100).roundToInt()
                    // we add "so far" to the amount
                    if (fiatCurrencyAmountState != FiatCurrencyConversionRateState.Unavailable) {
                        fiatCurrencyAmountText = context.getString(
                            R.string.home_status_syncing_amount_suffix,
                            fiatCurrencyAmountText
                        )
                    }
                    statusText = if (progressPercent == 0) {
                        context.getString(R.string.ns_preparing_download)
                    } else {
                        context.getString(R.string.ns_downloading_wallet, progressPercent)
                    }
                }
                Synchronizer.Status.VALIDATING -> {
                    statusIconDrawable = R.drawable.ic_icon_validating
                    statusText = context.getString(R.string.ns_validating)
                }
                Synchronizer.Status.SCANNING -> {
                    // SDK provides us only one progress, which keeps on 100 in the scanning state
                    progress = PercentDecimal.ONE_HUNDRED_PERCENT
                    statusText = context.getString(R.string.ns_finalizing)
                    statusIconDrawable = R.drawable.ic_icon_preparing
                }
                Synchronizer.Status.SYNCED,
                Synchronizer.Status.ENHANCING -> {
                    statusText = if (updateAvailable) {
                        context.getString(R.string.home_status_update)
                    } else {
                        context.getString(R.string.ns_enhancing)
                    }
                    statusIconDrawable = R.drawable.ic_icon_validating
                }
                Synchronizer.Status.DISCONNECTED -> {
                    statusText = context.getString(
                        R.string.home_status_error,
                        context.getString(R.string.home_status_error_connection)
                    )
                    statusIconDrawable = R.drawable.ic_icon_reconnecting
                }
                Synchronizer.Status.STOPPED -> {
                    statusText = context.getString(R.string.home_status_stopped)
                    statusIconDrawable = R.drawable.ic_icon_connecting
                }
            }

            // more detailed error message
            walletSnapshot.synchronizerError?.let {
                statusText = context.getString(
                    R.string.home_status_error,
                    walletSnapshot.synchronizerError.getCauseMessage()
                        ?: context.getString(R.string.home_status_error_unknown)
                )
                statusIconDrawable = R.drawable.ic_icon_reconnecting
            }

            return WalletDisplayValues(
                progress = progress,
                zecAmountText = zecAmountText,
                statusText = statusText,
                fiatCurrencyAmountState = fiatCurrencyAmountState,
                fiatCurrencyAmountText = fiatCurrencyAmountText,
                statusIconDrawable = statusIconDrawable
            )
        }
    }
}

private fun getFiatCurrencyRateValue(
    context: Context,
    fiatCurrencyAmountState: FiatCurrencyConversionRateState
): String {
    return fiatCurrencyAmountState.let { state ->
        when (state) {
            is FiatCurrencyConversionRateState.Current -> state.formattedFiatValue
            is FiatCurrencyConversionRateState.Stale -> state.formattedFiatValue
            is FiatCurrencyConversionRateState.Unavailable -> {
                context.getString(R.string.fiat_currency_conversion_rate_unavailable)
            }
        }
    }
}
