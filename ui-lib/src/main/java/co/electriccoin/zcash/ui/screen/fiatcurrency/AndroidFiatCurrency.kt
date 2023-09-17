package co.electriccoin.zcash.ui.screen.fiatcurrency

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.electriccoin.zcash.ui.MainActivity
import co.electriccoin.zcash.ui.screen.fiatcurrency.model.FiatCurrency
import co.electriccoin.zcash.ui.screen.fiatcurrency.view.FiatCurrency
import co.electriccoin.zcash.ui.screen.fiatcurrency.viewmodel.FiatCurrencyViewModel
import co.electriccoin.zcash.ui.screen.home.viewmodel.HomeViewModel

@Composable
internal fun MainActivity.AndroidFiatCurrency(onBack: () -> Unit) {
    WrapFiatCurrency(activity = this, onBack = onBack)
}

@Composable
internal fun WrapFiatCurrency(activity: ComponentActivity, onBack: () -> Unit) {
    val homeViewModel by activity.viewModels<HomeViewModel>()
    val fiatCurrencyViewModel = viewModel<FiatCurrencyViewModel>()
    val preferredFiatCurrency by fiatCurrencyViewModel.preferredFiatCurrency.collectAsStateWithLifecycle()

    val updateFiatCurrency = { selected: FiatCurrency ->
        fiatCurrencyViewModel.updateFiatCurrency(selected)
        if (selected != FiatCurrency.OFF) {
            homeViewModel.getZecPriceFromCoinMetrics(selected.serverUrl)
        }
    }

    FiatCurrency(
        preferredFiatCurrency = preferredFiatCurrency,
        onBack = onBack,
        onPreferredFiatCurrencyUpdated = updateFiatCurrency
    )
}
