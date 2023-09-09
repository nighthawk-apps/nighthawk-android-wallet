package co.electriccoin.zcash.ui.screen.wallet.model

import android.content.Context
import androidx.annotation.DrawableRes
import cash.z.ecc.android.sdk.model.toZecString
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.toBalanceUiModel
import co.electriccoin.zcash.ui.common.toBalanceValueModel
import co.electriccoin.zcash.ui.screen.fiatcurrency.model.FiatCurrencyUiState
import co.electriccoin.zcash.ui.screen.home.model.WalletSnapshot

data class BalanceDisplayValues(
    @DrawableRes val iconDrawableRes: Int,
    val balanceType: String,
    val balanceUIModel: BalanceUIModel,
    val msg: String?
) {
    companion object {
        internal fun getNextValue(
            context: Context,
            balanceViewType: BalanceViewType,
            walletSnapshot: WalletSnapshot,
            isFiatCurrencyPreferred: Boolean,
            fiatCurrencyUiState: FiatCurrencyUiState
        ): BalanceDisplayValues {
            var iconDrawableRes = R.drawable.ic_icon_left_swipe
            val selectedDenomination = context.getString(R.string.ns_zec)
            var balanceType = ""
            var msg: String? = null
            var balanceUIModel = BalanceValuesModel().toBalanceUiModel(context)

            when (balanceViewType) {
                BalanceViewType.SWIPE -> {
                    iconDrawableRes = R.drawable.ic_icon_left_swipe
                    msg = context.getString(R.string.ns_swipe_left)
                }

                BalanceViewType.TOTAL -> {
                    val totalBalance =
                        walletSnapshot.saplingBalance.total.plus(walletSnapshot.transparentBalance.total)
                    val availableBalance =
                        walletSnapshot.saplingBalance.available.plus(walletSnapshot.transparentBalance.available)
                    iconDrawableRes = R.drawable.ic_icon_total
                    balanceType = context.getString(R.string.ns_total_balance)
                    if (totalBalance > availableBalance) {
                        msg = context.getString(
                            R.string.ns_expecting_balance_snack_bar_msg,
                            (totalBalance - availableBalance).toZecString()
                        )
                    }
                    balanceUIModel = availableBalance.toBalanceValueModel(fiatCurrencyUiState, isFiatCurrencyPreferred, selectedDenomination).toBalanceUiModel(context)
                }
                BalanceViewType.SHIELDED -> {
                    iconDrawableRes = R.drawable.ic_icon_shielded
                    balanceType = context.getString(R.string.ns_shielded_balance)
                    if (walletSnapshot.saplingBalance.total > walletSnapshot.saplingBalance.available) {
                        msg = context.getString(
                            R.string.ns_expecting_balance_snack_bar_msg,
                            (walletSnapshot.saplingBalance.total - walletSnapshot.saplingBalance.available).toZecString()
                        )
                    }
                    val availableBalance = walletSnapshot.saplingBalance.available
                    balanceUIModel = availableBalance.toBalanceValueModel(fiatCurrencyUiState, isFiatCurrencyPreferred).toBalanceUiModel(context)
                }
                BalanceViewType.TRANSPARENT -> {
                    iconDrawableRes = R.drawable.ic_icon_transparent
                    balanceType = context.getString(R.string.ns_transparent_balance)
                    if (walletSnapshot.transparentBalance.total > walletSnapshot.transparentBalance.available) {
                        msg = context.getString(
                            R.string.ns_expecting_balance_snack_bar_msg,
                            (walletSnapshot.transparentBalance.total - walletSnapshot.transparentBalance.available).toZecString()
                        )
                    }
                    val availableBalance = walletSnapshot.transparentBalance.available
                    balanceUIModel = availableBalance.toBalanceValueModel(fiatCurrencyUiState, isFiatCurrencyPreferred).toBalanceUiModel(context)
                }
            }
            return BalanceDisplayValues(
                iconDrawableRes,
                balanceType,
                balanceUIModel,
                msg
            )
        }
    }
}