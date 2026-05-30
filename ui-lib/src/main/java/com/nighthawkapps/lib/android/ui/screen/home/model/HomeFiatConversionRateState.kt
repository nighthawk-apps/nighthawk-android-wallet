package com.nighthawkapps.lib.android.ui.screen.home.model

/** Fiat conversion display state for the home header. */
sealed class HomeFiatConversionRateState {
    data class Current(
        val formattedFiatValue: String
    ) : HomeFiatConversionRateState()

    data class Stale(
        val formattedFiatValue: String
    ) : HomeFiatConversionRateState()

    data object Unavailable : HomeFiatConversionRateState()
}
