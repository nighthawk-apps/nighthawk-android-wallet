package co.electriccoin.zcash.ui.screen.fiatcurrency.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BodyMedium
import co.electriccoin.zcash.ui.design.component.TitleMedium
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.screen.fiatcurrency.model.FiatCurrency

@Preview
@Composable
fun FiatCurrencyPreview() {
    ZcashTheme(darkTheme = true) {
        Surface {
            FiatCurrency(
                preferredFiatCurrency = FiatCurrency.OFF,
                onBack = {},
                onPreferredFiatCurrencyUpdated = {}
            )
        }
    }
}

@Composable
fun FiatCurrency(
    preferredFiatCurrency: FiatCurrency,
    onBack: () -> Unit,
    onPreferredFiatCurrencyUpdated: (FiatCurrency) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(dimensionResource(id = R.dimen.screen_standard_margin))
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(dimensionResource(id = R.dimen.back_icon_size))
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = stringResource(R.string.receive_back_content_description)
            )
        }
        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.offset)))
        TitleMedium(
            text = stringResource(id = R.string.ns_fiat_currency),
            color = colorResource(id = co.electriccoin.zcash.ui.design.R.color.ns_parmaviolet)
        )
        Spacer(modifier = Modifier.height(24.dp))
        BodyMedium(text = stringResource(id = R.string.ns_fiat_currency_body))
        Spacer(modifier = Modifier.height(24.dp))

        FiatCurrency.values().forEach {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = it == preferredFiatCurrency,
                    onClick = { onPreferredFiatCurrencyUpdated(it) })
                Spacer(modifier = Modifier.width(8.dp))
                BodyMedium(text = it.currencyText)
            }
        }
    }
}
