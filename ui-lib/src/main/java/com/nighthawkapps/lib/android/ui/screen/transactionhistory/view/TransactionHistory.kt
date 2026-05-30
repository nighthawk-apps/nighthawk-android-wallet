package com.nighthawkapps.lib.android.ui.screen.transactionhistory.view

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiTransactionOverview
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.design.component.TitleLarge
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
                .padding(dimensionResource(id = R.dimen.screen_standard_margin))
    ) {
        Box {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(dimensionResource(id = R.dimen.back_icon_size))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.receive_back_content_description)
                )
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimensionResource(id = R.dimen.back_icon_size)),
                contentAlignment = Alignment.Center
            ) {
                TitleLarge(
                    text = stringResource(id = R.string.ns_transaction_history),
                    textAlign = TextAlign.Center
                )
            }
        }
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
