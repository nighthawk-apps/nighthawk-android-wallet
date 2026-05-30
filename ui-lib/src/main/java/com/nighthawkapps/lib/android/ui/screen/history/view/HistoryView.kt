package com.nighthawkapps.lib.android.ui.screen.history.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.outlined.ArrowCircleDown
import androidx.compose.material.icons.outlined.ArrowCircleUp
import androidx.compose.material.icons.twotone.ArrowCircleDown
import androidx.compose.material.icons.twotone.ArrowCircleUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.TopCenter
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiAmountFormatter
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiTransactionOverview
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.removeTrailingZero
import com.nighthawkapps.lib.android.ui.design.component.Body
import com.nighthawkapps.lib.android.ui.design.component.GradientSurface
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme
import com.nighthawkapps.lib.android.ui.screen.history.HistoryTag
import com.nighthawkapps.lib.android.ui.screen.history.state.TransactionHistorySyncState
import com.nighthawkapps.lib.android.ui.screen.transactiondetails.model.DarkfiTransactionUiState
import com.nighthawkapps.lib.android.ui.screen.transactiondetails.model.uiTransactionState
import kotlinx.collections.immutable.ImmutableList
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Preview("History")
@Composable
private fun ComposablePreview() {
    WalletTheme(darkTheme = true) {
        GradientSurface {
            History(
                transactionState = TransactionHistorySyncState.Loading,
                goBack = {}
            )
        }
    }
}

val dateFormat: DateFormat by lazy {
    SimpleDateFormat.getDateTimeInstance(
        SimpleDateFormat.MEDIUM,
        SimpleDateFormat.SHORT,
        Locale.getDefault()
    )
}

@Composable
fun History(
    transactionState: TransactionHistorySyncState,
    goBack: () -> Unit
) {
    Scaffold(topBar = {
        HistoryTopBar(onBack = goBack)
    }) { paddingValues ->
        HistoryMainContent(
            transactionState = transactionState,
            modifier =
                Modifier
                    .fillMaxHeight()
                    .padding(
                        top = paddingValues.calculateTopPadding(),
                        bottom = paddingValues.calculateBottomPadding()
                    )
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HistoryTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = { Text(text = stringResource(id = R.string.history_title)) },
        navigationIcon = {
            IconButton(
                onClick = onBack
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.history_back_content_description)
                )
            }
        }
    )
}

@Composable
private fun HistoryMainContent(
    transactionState: TransactionHistorySyncState,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (transactionState) {
            is TransactionHistorySyncState.Loading -> {
                CircularProgressIndicator(
                    modifier =
                        Modifier
                            .align(alignment = Center)
                            .testTag(HistoryTag.PROGRESS)
                )
            }

            is TransactionHistorySyncState.Syncing -> {
                Column(
                    modifier = Modifier.align(alignment = TopCenter)
                ) {
                    Body(
                        text = stringResource(id = R.string.history_syncing),
                        modifier =
                            Modifier
                                .padding(
                                    top = WalletTheme.dimens.spacingSmall,
                                    bottom = WalletTheme.dimens.spacingSmall,
                                    start = WalletTheme.dimens.spacingDefault,
                                    end = WalletTheme.dimens.spacingDefault
                                )
                    )
                    HistoryList(transactions = transactionState.transactions)
                }
                // Add progress indicator only in the state of empty transaction
                if (transactionState.hasNoTransactions()) {
                    CircularProgressIndicator(
                        modifier =
                            Modifier
                                .align(alignment = Center)
                                .testTag(HistoryTag.PROGRESS)
                    )
                }
            }

            is TransactionHistorySyncState.Done -> {
                if (transactionState.hasNoTransactions()) {
                    Body(
                        text = stringResource(id = R.string.history_empty),
                        modifier =
                            Modifier
                                .padding(all = WalletTheme.dimens.spacingDefault)
                                .align(alignment = Center)
                    )
                } else {
                    HistoryList(transactions = transactionState.transactions)
                }
            }
        }
    }
}

@Composable
private fun HistoryList(transactions: ImmutableList<DarkfiTransactionOverview>) {
    LazyColumn(
        contentPadding = PaddingValues(all = WalletTheme.dimens.spacingDefault),
        modifier = Modifier.testTag(HistoryTag.TRANSACTION_LIST),
    ) {
        items(transactions) {
            HistoryItem(transaction = it)
        }
    }
}

@Composable
@Suppress("LongMethod")
fun HistoryItem(transaction: DarkfiTransactionOverview,) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = WalletTheme.dimens.spacingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val transactionText: String
        val transactionIcon: ImageVector
        when (transaction.getExtendedState()) {
            TransactionExtendedState.SENT -> {
                transactionText = stringResource(id = R.string.history_item_sent)
                transactionIcon = Icons.TwoTone.ArrowCircleUp
            }

            TransactionExtendedState.SENDING -> {
                transactionText = stringResource(id = R.string.history_item_sending)
                transactionIcon = Icons.Outlined.ArrowCircleUp
            }

            TransactionExtendedState.RECEIVED -> {
                transactionText = stringResource(id = R.string.history_item_received)
                transactionIcon = Icons.TwoTone.ArrowCircleDown
            }

            TransactionExtendedState.RECEIVING -> {
                transactionText = stringResource(id = R.string.history_item_receiving)
                transactionIcon = Icons.Outlined.ArrowCircleDown
            }

            TransactionExtendedState.EXPIRED -> {
                transactionText = stringResource(id = R.string.history_item_expired)
                transactionIcon = Icons.Filled.Cancel
            }
        }

        Image(
            imageVector = transactionIcon,
            contentDescription = transactionText,
            modifier = Modifier.padding(all = WalletTheme.dimens.spacingTiny)
        )

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Body(
                text = transactionText,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(WalletTheme.dimens.spacingTiny))

            val millis = transaction.timestampEpochMillis
            val dateString = millis?.let { dateFormat.format(Date(it)) } ?: ""
            Body(
                text = dateString,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column {
            Row(modifier = Modifier.align(alignment = Alignment.End)) {
                val formatted =
                    DarkfiAmountFormatter.formatAtomic(transaction.netValueAtomic).removeTrailingZero()
                val formattedSignedAmount =
                    if (transaction.isSentTransaction) {
                        "-$formatted"
                    } else {
                        formatted
                    }
                Body(text = formattedSignedAmount)

                Spacer(modifier = Modifier.width(WalletTheme.dimens.spacingTiny))

                Body(text = "DRK")
            }
        }
    }
}

enum class TransactionExtendedState {
    SENT,
    SENDING,
    RECEIVED,
    RECEIVING,
    EXPIRED
}

private fun DarkfiTransactionOverview.getExtendedState(): TransactionExtendedState =
    when (uiTransactionState) {
        DarkfiTransactionUiState.Expired -> {
            TransactionExtendedState.EXPIRED
        }

        DarkfiTransactionUiState.Confirmed -> {
            if (isSentTransaction) {
                TransactionExtendedState.SENT
            } else {
                TransactionExtendedState.RECEIVED
            }
        }

        DarkfiTransactionUiState.Pending -> {
            if (isSentTransaction) {
                TransactionExtendedState.SENDING
            } else {
                TransactionExtendedState.RECEIVING
            }
        }
    }
