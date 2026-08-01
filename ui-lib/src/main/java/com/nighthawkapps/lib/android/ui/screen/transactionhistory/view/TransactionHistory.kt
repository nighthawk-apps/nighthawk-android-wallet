package com.nighthawkapps.lib.android.ui.screen.transactionhistory.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiTransactionOverview
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.design.component.NighthawkTopBar
import com.nighthawkapps.lib.android.ui.design.component.NighthawkTopBarLeading
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme
import com.nighthawkapps.lib.android.ui.fixture.DarkfiTransactionOverviewFixture
import com.nighthawkapps.lib.android.ui.screen.fiatcurrency.model.FiatCurrency
import com.nighthawkapps.lib.android.ui.screen.fiatcurrency.model.FiatCurrencyUiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList

@Preview
@Composable
fun TransactionHistoryPreview() {
    WalletTheme(darkTheme = false) {
        Surface {
            val list =
                listOf(
                    DarkfiTransactionOverviewFixture.new(isSentTransaction = true),
                    DarkfiTransactionOverviewFixture.new(),
                )
            TransactionHistory(
                transactionSnapshot = list.toPersistentList(),
                fiatCurrencyUiState = FiatCurrencyUiState(FiatCurrency.USD, 25.25),
                isFiatCurrencyPreferred = false,
                onBack = {},
                onTransactionDetail = {},
                onItemLongClick = {}
            )
        }
    }
}

@Composable
fun TransactionHistory(
    transactionSnapshot: ImmutableList<DarkfiTransactionOverview>,
    fiatCurrencyUiState: FiatCurrencyUiState,
    isFiatCurrencyPreferred: Boolean,
    onBack: () -> Unit,
    onTransactionDetail: (String) -> Unit,
    onItemLongClick: (DarkfiTransactionOverview) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = WalletTheme.dimens.spacingDefault)
                .padding(vertical = dimensionResource(id = R.dimen.screen_standard_margin))
    ) {
        NighthawkTopBar(
            onLeadingClick = onBack,
            leading = NighthawkTopBarLeading.Back,
            title = stringResource(id = R.string.ns_transaction_history),
        )
        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.screen_standard_margin)))
        LazyColumn {
            items(transactionSnapshot) { transactionOverview ->
                TransactionOverviewHistoryRow(
                    transactionOverview = transactionOverview,
                    fiatCurrencyUiState = fiatCurrencyUiState,
                    isFiatCurrencyPreferred = isFiatCurrencyPreferred,
                    onItemClick = { onTransactionDetail(it.rawId) },
                    onItemLongClick = onItemLongClick
                )
            }
        }
    }
}
