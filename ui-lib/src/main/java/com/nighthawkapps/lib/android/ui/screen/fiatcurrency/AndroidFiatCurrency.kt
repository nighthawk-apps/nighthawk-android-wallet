package com.nighthawkapps.lib.android.ui.screen.fiatcurrency

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.screen.fiatcurrency.model.FiatCurrency
import com.nighthawkapps.lib.android.ui.screen.fiatcurrency.view.FiatCurrency
import com.nighthawkapps.lib.android.ui.screen.fiatcurrency.viewmodel.FiatCurrencyViewModel
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.HomeViewModel

@Composable
internal fun MainActivity.AndroidFiatCurrency(onBack: () -> Unit) {
    WrapFiatCurrency(activity = this, onBack = onBack)
}

@Composable
internal fun WrapFiatCurrency(
    activity: ComponentActivity,
    onBack: () -> Unit
) {
    val homeViewModel by activity.viewModels<HomeViewModel>()
    val fiatCurrencyViewModel = viewModel<FiatCurrencyViewModel>()
    val preferredFiatCurrency by fiatCurrencyViewModel.preferredFiatCurrency.collectAsStateWithLifecycle()

    val updateFiatCurrency = { selected: FiatCurrency ->
        fiatCurrencyViewModel.updateFiatCurrency(selected)
        if (selected != FiatCurrency.OFF) {
            homeViewModel.refreshSpotPrice(selected.serverUrl)
        }
    }

    FiatCurrency(
        preferredFiatCurrency = preferredFiatCurrency,
        onBack = onBack,
        onPreferredFiatCurrencyUpdated = updateFiatCurrency
    )
}
