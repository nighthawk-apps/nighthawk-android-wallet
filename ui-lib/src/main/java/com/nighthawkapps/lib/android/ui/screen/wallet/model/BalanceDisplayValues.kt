package com.nighthawkapps.lib.android.ui.screen.wallet.model

import android.content.Context
import androidx.annotation.DrawableRes
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.toBalanceUiModel
import com.nighthawkapps.lib.android.ui.screen.fiatcurrency.model.FiatCurrencyUiState
import com.nighthawkapps.lib.android.ui.screen.home.model.WalletSnapshot

data class BalanceDisplayValues(
    @DrawableRes val iconDrawableRes: Int,
    val balanceType: String,
    val balanceUIModel: BalanceUIModel,
    val msg: String?,
) {
    companion object {
        @Suppress("UNUSED_PARAMETER")
        internal fun getNextValue(
            context: Context,
            balanceViewType: BalanceViewType,
            walletSnapshot: WalletSnapshot,
            isFiatCurrencyPreferred: Boolean,
            fiatCurrencyUiState: FiatCurrencyUiState,
        ): BalanceDisplayValues {
            var iconDrawableRes = R.drawable.ic_icon_left_swipe
            var balanceType = ""
            var msg: String? = null
            val balanceUIModel = BalanceValuesModel().toBalanceUiModel(context)

            when (balanceViewType) {
                BalanceViewType.SWIPE -> {
                    iconDrawableRes = R.drawable.ic_icon_left_swipe
                    msg = context.getString(R.string.ns_swipe_left)
                }

                BalanceViewType.ASSETS -> {
                    iconDrawableRes = R.drawable.ic_icon_total
                    balanceType = context.getString(R.string.ns_wallet_assets_title)
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
