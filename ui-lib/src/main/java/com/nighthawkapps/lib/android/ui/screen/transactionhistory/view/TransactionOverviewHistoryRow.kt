package com.nighthawkapps.lib.android.ui.screen.transactionhistory.view

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiTransactionOverview
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.toBalanceValueModel
import com.nighthawkapps.lib.android.ui.design.component.Body
import com.nighthawkapps.lib.android.ui.design.component.BodySmall
import com.nighthawkapps.lib.android.ui.design.component.MaxWidthHorizontalDivider
import com.nighthawkapps.lib.android.ui.design.component.TitleMedium
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme
import com.nighthawkapps.lib.android.ui.fixture.DarkfiTransactionOverviewFixture
import com.nighthawkapps.lib.android.ui.screen.fiatcurrency.model.FiatCurrency
import com.nighthawkapps.lib.android.ui.screen.fiatcurrency.model.FiatCurrencyUiState
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Preview
@Composable
fun TransactionOverviewHistoryRowPreview() {
    WalletTheme(darkTheme = false) {
        Surface {
            TransactionOverviewHistoryRow(
                transactionOverview = DarkfiTransactionOverviewFixture.new(),
                fiatCurrencyUiState = FiatCurrencyUiState(FiatCurrency.USD, 25.24),
                isFiatCurrencyPreferred = true,
                onItemClick = {},
                onItemLongClick = {},
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionOverviewHistoryRow(
    transactionOverview: DarkfiTransactionOverview,
    fiatCurrencyUiState: FiatCurrencyUiState,
    isBalancePrivateMode: Boolean = false,
    isFiatCurrencyPreferred: Boolean = false,
    onItemClick: (DarkfiTransactionOverview) -> Unit,
    onItemLongClick: (DarkfiTransactionOverview) -> Unit = {},
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onItemClick(transactionOverview) },
                    onLongClick = { onItemLongClick(transactionOverview) },
                ),
    ) {
        Spacer(modifier = Modifier.height(10.dp))
        Row {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_icon_downloading),
                contentDescription = null,
                modifier = Modifier.rotate(if (transactionOverview.isSentTransaction) 180f else 0f),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                TitleMedium(
                    text =
                        stringResource(
                            id =
                                if (transactionOverview.isSentTransaction) {
                                    R.string.ns_sent
                                } else {
                                    R.string.ns_received
                                },
                        ),
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(4.dp))
                val timeText =
                    transactionOverview.timestampEpochMillis?.let { ms ->
                        LocalDateTime
                            .ofInstant(
                                java.time.Instant.ofEpochMilli(ms),
                                ZoneOffset.UTC
                            ).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT))
                    } ?: stringResource(id = R.string.ns_transaction_date_error)
                BodySmall(text = timeText)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Spacer(modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                val transactionValueAtomic = transactionOverview.netValueAtomic
                val balanceValuesModel =
                    transactionValueAtomic.toBalanceValueModel(fiatCurrencyUiState, isFiatCurrencyPreferred)
                val transactionText =
                    (if (isBalancePrivateMode) "---" else balanceValuesModel.balance) +
                        " ${balanceValuesModel.balanceUnit}"
                Body(text = transactionText, color = colorResource(id = com.nighthawkapps.lib.android.ui.design.R.color.ns_parmaviolet))
                if (fiatCurrencyUiState.fiatCurrency != FiatCurrency.OFF) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val fiatCurrencyText =
                        (if (isBalancePrivateMode) "---" else balanceValuesModel.fiatBalance) +
                            " ${balanceValuesModel.fiatUnit}"
                    BodySmall(text = fiatCurrencyText, textAlign = TextAlign.End)
                }
            }
        }
        Spacer(modifier = Modifier.height(15.dp))
        MaxWidthHorizontalDivider()
    }
}
