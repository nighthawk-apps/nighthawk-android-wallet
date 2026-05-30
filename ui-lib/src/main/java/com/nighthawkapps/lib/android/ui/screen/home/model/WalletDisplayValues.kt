package com.nighthawkapps.lib.android.ui.screen.home.model

import android.content.Context
import androidx.annotation.DrawableRes
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiAmountFormatter
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiSyncStatus
import com.nighthawkapps.lib.android.ui.R

data class WalletDisplayValues(
    val progress: com.nighthawkapps.lib.android.sdk.wallet.DarkfiPercent,
    val balanceAmountText: String,
    val statusText: String,
    val fiatCurrencyAmountState: HomeFiatConversionRateState,
    val fiatCurrencyAmountText: String,
    @DrawableRes val statusIconDrawable: Int,
) {
    companion object {
        @Suppress("MagicNumber", "LongMethod")
        fun getNextValues(
            context: Context,
            walletSnapshot: WalletSnapshot,
            updateAvailable: Boolean,
        ): WalletDisplayValues {
            var progress = com.nighthawkapps.lib.android.sdk.wallet.DarkfiPercent.ZERO_PERCENT
            var statusText = context.getString(R.string.ns_connecting)
            val fiatCurrencyAmountState = HomeFiatConversionRateState.Unavailable
            val fiatCurrencyAmountText =
                context.getString(R.string.fiat_currency_conversion_rate_unavailable)
            var statusIconDrawable = R.drawable.ic_icon_connecting

            when (walletSnapshot.status) {
                DarkfiSyncStatus.SYNCING -> {
                    val progressPercent = (walletSnapshot.progress.decimal * 100)
                    progress = walletSnapshot.progress
                    statusText =
                        when (progressPercent) {
                            0f -> {
                                statusIconDrawable = R.drawable.ic_icon_preparing
                                context.getString(R.string.ns_preparing_scan)
                            }

                            100f -> {
                                statusIconDrawable = R.drawable.ic_icon_preparing
                                context.getString(R.string.ns_finalizing)
                            }

                            else -> {
                                statusIconDrawable = R.drawable.ic_icon_syncing
                                context.getString(
                                    R.string.ns_syncing_wallet,
                                    progressPercent,
                                )
                            }
                        }
                }

                DarkfiSyncStatus.SYNCED -> {
                    statusText =
                        if (updateAvailable) {
                            context.getString(R.string.home_status_update)
                        } else {
                            context.getString(R.string.ns_enhancing)
                        }
                    statusIconDrawable = R.drawable.ic_icon_validating
                }

                DarkfiSyncStatus.DISCONNECTED -> {
                    statusText =
                        context.getString(
                            R.string.home_status_error,
                            context.getString(R.string.home_status_error_connection),
                        )
                    statusIconDrawable = R.drawable.ic_icon_reconnecting
                }

                DarkfiSyncStatus.STOPPED -> {
                    statusText = context.getString(R.string.home_status_stopped)
                    statusIconDrawable = R.drawable.ic_icon_connecting
                }
            }

            walletSnapshot.walletError?.let {
                statusText =
                    context.getString(
                        R.string.home_status_error,
                        it.causeMessage()
                            ?: it.stackTraceSnippet()
                            ?: context.getString(R.string.home_status_error_unknown),
                    )
                statusIconDrawable = R.drawable.ic_icon_reconnecting
            }

            val balanceText =
                DarkfiAmountFormatter.formatAtomic(walletSnapshot.confirmedBalanceAtomic)

            return WalletDisplayValues(
                progress = progress,
                balanceAmountText = balanceText,
                statusText = statusText,
                fiatCurrencyAmountState = fiatCurrencyAmountState,
                fiatCurrencyAmountText = fiatCurrencyAmountText,
                statusIconDrawable = statusIconDrawable,
            )
        }
    }
}
