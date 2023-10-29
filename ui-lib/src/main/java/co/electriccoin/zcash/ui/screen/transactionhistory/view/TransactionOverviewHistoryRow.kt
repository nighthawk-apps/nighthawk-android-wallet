package co.electriccoin.zcash.ui.screen.transactionhistory.view

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
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.z.ecc.android.sdk.fixture.TransactionOverviewFixture
import cash.z.ecc.android.sdk.model.TransactionOverview
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.toBalanceValueModel
import co.electriccoin.zcash.ui.design.component.Body
import co.electriccoin.zcash.ui.design.component.BodySmall
import co.electriccoin.zcash.ui.design.component.MaxWidthHorizontalDivider
import co.electriccoin.zcash.ui.design.component.TitleMedium
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.screen.fiatcurrency.model.FiatCurrency
import co.electriccoin.zcash.ui.screen.fiatcurrency.model.FiatCurrencyUiState
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Preview
@Composable
fun TransactionOverviewHistoryRowPreview() {
    ZcashTheme(darkTheme = false) {
        Surface {
            TransactionOverviewHistoryRow(
                transactionOverview = TransactionOverviewFixture.new(),
                fiatCurrencyUiState = FiatCurrencyUiState(FiatCurrency.USD, 25.24),
                isFiatCurrencyPreferred = true,
                onItemClick = {},
                onItemLongClick = {})
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionOverviewHistoryRow(
    transactionOverview: TransactionOverview,
    fiatCurrencyUiState: FiatCurrencyUiState,
    isBalancePrivateMode: Boolean = false,
    isFiatCurrencyPreferred: Boolean = false,
    onItemClick: (TransactionOverview) -> Unit,
    onItemLongClick: (TransactionOverview) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onItemClick(transactionOverview) },
                onLongClick = { onItemLongClick(transactionOverview) }
            )
    ) {
        Spacer(modifier = Modifier.height(10.dp))
        Row {
            Icon(
                painter = painterResource(id = R.drawable.ic_icon_downloading),
                contentDescription = null,
                modifier = Modifier.rotate(if (transactionOverview.isSentTransaction) 180f else 0f)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                TitleMedium(text = stringResource(id = if (transactionOverview.isSentTransaction) R.string.ns_sent else R.string.ns_received), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(4.dp))
                val timeText = transactionOverview.blockTimeEpochSeconds?.let {
                    Instant.fromEpochSeconds(it).toLocalDateTime(TimeZone.UTC).toJavaLocalDateTime().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT))
                } ?: stringResource(id = R.string.ns_transaction_date_error)
                BodySmall(text = timeText)
            }
            Spacer(modifier = Modifier.width(4.dp))
            if (transactionOverview.memoCount > 0) {
                Icon(painter = painterResource(id = R.drawable.ic_icon_memo), contentDescription = null, modifier = Modifier.padding(vertical = 4.dp))
            }
            Spacer(modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                val transactionValue = transactionOverview.netValue - (transactionOverview.feePaid ?: Zatoshi(0))
                val balanceValuesModel = transactionValue.toBalanceValueModel(fiatCurrencyUiState, isFiatCurrencyPreferred)
                val transactionText = (if (isBalancePrivateMode) "---" else balanceValuesModel.balance) + " ${balanceValuesModel.balanceUnit}"
                Body(text = transactionText, color = colorResource(id = co.electriccoin.zcash.ui.design.R.color.ns_parmaviolet))
                if (fiatCurrencyUiState.fiatCurrency != FiatCurrency.OFF) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val fiatCurrencyText =
                        (if (isBalancePrivateMode) "---" else balanceValuesModel.fiatBalance) + " ${balanceValuesModel.fiatUnit}"
                    BodySmall(text = fiatCurrencyText, textAlign = TextAlign.End)
                }
            }
        }
        Spacer(modifier = Modifier.height(15.dp))
        MaxWidthHorizontalDivider()
    }
}