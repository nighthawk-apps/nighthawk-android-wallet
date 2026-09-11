package com.nighthawkapps.lib.android.ui.screen.wallet.model

import android.content.Context
import androidx.annotation.DrawableRes
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiAmountFormatter
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.removeTrailingZero
import com.nighthawkapps.lib.android.ui.common.toBalanceUiModel
import com.nighthawkapps.lib.android.ui.common.toBalanceValueModel
import com.nighthawkapps.lib.android.ui.screen.fiatcurrency.model.FiatCurrencyUiState
import com.nighthawkapps.lib.android.ui.screen.home.model.WalletSnapshot
import com.nighthawkapps.lib.android.ui.screen.home.model.spendableBalanceAtomic
import com.nighthawkapps.lib.android.ui.screen.home.model.totalBalanceAtomic

data class BalanceDisplayValues(
    @DrawableRes val iconDrawableRes: Int,
    val balanceType: String,
    val balanceUIModel: BalanceUIModel,
    val msg: String?,
) {
    companion object {
        internal fun getNextValue(
            context: Context,
            balanceViewType: BalanceViewType,
            walletSnapshot: WalletSnapshot,
            isFiatCurrencyPreferred: Boolean,
            fiatCurrencyUiState: FiatCurrencyUiState,
        ): BalanceDisplayValues {
            var iconDrawableRes = R.drawable.ic_icon_left_swipe
            val selectedDenomination = "DRK"
            var balanceType = ""
            var msg: String? = null
            var balanceUIModel = BalanceValuesModel().toBalanceUiModel(context)

            when (balanceViewType) {
                BalanceViewType.SWIPE -> {
                    iconDrawableRes = R.drawable.ic_icon_left_swipe
                    msg = context.getString(R.string.ns_swipe_left)
                }

                BalanceViewType.TOTAL -> {
                    val totalBalance = walletSnapshot.totalBalanceAtomic()
                    val availableBalance = walletSnapshot.spendableBalanceAtomic()
                    iconDrawableRes = R.drawable.ic_icon_total
                    balanceType = context.getString(R.string.ns_wallet_balance)
                    if (totalBalance > availableBalance) {
                        msg =
                            context.getString(
                                R.string.ns_expecting_balance_snack_bar_msg,
                                DarkfiAmountFormatter.formatAtomic(totalBalance - availableBalance).removeTrailingZero(),
                            )
                    }
                    balanceUIModel =
                        availableBalance
                            .toBalanceValueModel(fiatCurrencyUiState, isFiatCurrencyPreferred, selectedDenomination)
                            .toBalanceUiModel(context)
                }

                is BalanceViewType.Token -> {
                    val token = balanceViewType.balance
                    iconDrawableRes = R.drawable.ic_icon_total
                    balanceType = token.displayName
                    balanceUIModel =
                        BalanceUIModel(
                            DarkfiAmountFormatter.formatAtomic(token.balanceAtomic).removeTrailingZero(),
                            token.displayName,
                            "",
                            "",
                        )
                }
            }
            return BalanceDisplayValues(
                iconDrawableRes,
                balanceType,
                balanceUIModel,
                msg,
            )
        }
    }
}
