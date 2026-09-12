package com.nighthawkapps.lib.android.ui.screen.transactiondetails.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiAmountFormatter
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiNetwork
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiSyncMethod
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiTransactionRecipient
import com.nighthawkapps.lib.android.spackle.Twig
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.AlertDialog
import com.nighthawkapps.lib.android.ui.common.addressTypeNameId
import com.nighthawkapps.lib.android.ui.common.darkfiExplorerUrlStringId
import com.nighthawkapps.lib.android.ui.common.removeTrailingZero
import com.nighthawkapps.lib.android.ui.design.component.BalanceText
import com.nighthawkapps.lib.android.ui.design.component.BodyMedium
import com.nighthawkapps.lib.android.ui.design.component.DottedBorderTextButton
import com.nighthawkapps.lib.android.ui.design.component.MaxWidthHorizontalDivider
import com.nighthawkapps.lib.android.ui.design.component.NighthawkTopBar
import com.nighthawkapps.lib.android.ui.design.component.NighthawkTopBarLeading
import com.nighthawkapps.lib.android.ui.design.component.TitleLarge
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme
import com.nighthawkapps.lib.android.ui.fixture.DarkfiTransactionOverviewFixture
import com.nighthawkapps.lib.android.ui.screen.transactiondetails.model.DarkfiTransactionUiState
import com.nighthawkapps.lib.android.ui.screen.transactiondetails.model.TransactionDetailsUIModel
import com.nighthawkapps.lib.android.ui.screen.transactiondetails.model.blockTimeEpochSeconds
import com.nighthawkapps.lib.android.ui.screen.transactiondetails.model.uiTransactionState
import com.nighthawkapps.lib.android.ui.screen.wallet.model.BalanceUIModel
import java.time.LocalDateTime
import java.time.ZoneOffset

@Preview
@Composable
fun TransactionDetailsPreview() {
    WalletTheme(darkTheme = false) {
        Surface {
            val transactionDetailsUIModel =
                TransactionDetailsUIModel(
                    transactionOverview = DarkfiTransactionOverviewFixture.new(),
                    transactionRecipient = DarkfiTransactionRecipient("jhasdgjhagsdjagsjadjhgasjhdgajshdgjahsgdjasgdjasgdjsad"),
                    network = DarkfiNetwork.Mainnet,
                    networkHeight = 1_234_567L,
                )
            TransactionDetails(
                transactionDetailsUIModel = transactionDetailsUIModel,
                balanceUIModel = BalanceUIModel("50", "DRK", "154.92", "USD"),
                onBack = {},
                viewOnBlockExplorer = { _, _ -> },
                isNavigateAwayFromAppWarningShown = false
            )
        }
    }
}

@Preview
@Composable
fun TransactionDetailsDarkPreview() {
    WalletTheme(darkTheme = true) {
        Surface {
            val transactionDetailsUIModel =
                TransactionDetailsUIModel(
                    transactionOverview = DarkfiTransactionOverviewFixture.new(),
                    transactionRecipient = DarkfiTransactionRecipient("jhasdgjhagsdjagsjadjhgasjhdgajshdgjahsgdjasgdjasgdjsad"),
                    network = DarkfiNetwork.Mainnet,
                    networkHeight = 1_234_567L,
                )
            TransactionDetails(
                transactionDetailsUIModel = transactionDetailsUIModel,
                balanceUIModel = BalanceUIModel("50", "DRK", "154.92", "USD"),
                onBack = {},
                viewOnBlockExplorer = { _, _ -> },
                isNavigateAwayFromAppWarningShown = false
            )
        }
    }
}

@Composable
fun TransactionDetails(
    transactionDetailsUIModel: TransactionDetailsUIModel?,
    balanceUIModel: BalanceUIModel,
    isNavigateAwayFromAppWarningShown: Boolean,
    onBack: () -> Unit,
    viewOnBlockExplorer: (url: String, updateWarningStatus: Boolean) -> Unit
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = WalletTheme.dimens.spacingDefault)
                .padding(vertical = dimensionResource(id = R.dimen.screen_standard_margin))
                .verticalScroll(rememberScrollState())
    ) {
        val showAppLeavingDialog =
            remember {
                mutableStateOf(false)
            }

        NighthawkTopBar(
            onLeadingClick = onBack,
            leading = NighthawkTopBarLeading.Back,
            title = null,
        )
        Image(
            painter = painterResource(id = R.drawable.ic_nighthawk_logo),
            contentDescription = stringResource(id = R.string.ns_logo_desc),
            contentScale = ContentScale.Inside,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(dimensionResource(id = R.dimen.pageMargin)))
        TitleLarge(
            text = stringResource(id = R.string.ns_nighthawk),
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.pageMargin)))
        BodyMedium(
            text = stringResource(id = R.string.ns_transaction_details),
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            color = WalletTheme.colors.secondaryTitleText
        )
        Spacer(modifier = Modifier.height(38.dp))

        val overview = transactionDetailsUIModel?.transactionOverview
        if (overview == null) {
            Twig.debug { "Transaction overview ui model is null" }
            BodyMedium(
                text = stringResource(id = R.string.ns_transaction_details_error_msg),
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                color = WalletTheme.colors.surfaceEnd
            )
        } else {
            Icon(
                painter = painterResource(id = R.drawable.ic_icon_downloading),
                contentDescription = null,
                modifier =
                    Modifier
                        .rotate(if (overview.isSentTransaction) 180f else 0f)
                        .align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(21.dp))

            // Amount section
            Row(
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                BalanceText(text = balanceUIModel.balance)
                Spacer(modifier = Modifier.width(4.dp))
                BalanceText(
                    text = balanceUIModel.balanceUnit,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            BodyMedium(
                text = "${balanceUIModel.fiatBalance} ${balanceUIModel.fiatUnit}",
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                color = WalletTheme.colors.secondaryTitleText
            )
            Spacer(
                modifier = Modifier.height(30.dp)
            )

            val (transactionStateTextId, transactionStateIconId) =
                when (overview.uiTransactionState) {
                    DarkfiTransactionUiState.Confirmed -> {
                        Pair(
                            R.string.ns_confirmed,
                            R.drawable.ic_icon_confirmed
                        )
                    }

                    DarkfiTransactionUiState.Pending -> {
                        Pair(R.string.ns_pending, R.drawable.ic_icon_preparing)
                    }

                    DarkfiTransactionUiState.Expired -> {
                        Pair(R.string.ns_expired, R.drawable.ic_done_24dp)
                    }
                }

            DottedBorderTextButton(
                onClick = {},
                text = stringResource(id = transactionStateTextId),
                modifier =
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .height(40.dp),
                borderColor = WalletTheme.colors.secondaryTitleText,
                startIcon = transactionStateIconId
            )

            Spacer(
                modifier = Modifier.heightIn(min = 50.dp)
            )

            if (overview.contractSummary.isNotBlank()) {
                BodyMedium(
                    text = stringResource(id = R.string.ns_transaction_type),
                    color = WalletTheme.colors.secondaryTitleText,
                )
                Spacer(modifier = Modifier.height(10.dp))
                BodyMedium(text = overview.contractSummary)
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Memo
            if (transactionDetailsUIModel.memo.isNotBlank()) {
                BodyMedium(
                    text = stringResource(id = R.string.ns_memo),
                    color = WalletTheme.colors.secondaryTitleText
                )
                Spacer(modifier = Modifier.height(10.dp))
                BodyMedium(text = transactionDetailsUIModel.memo)
                Spacer(modifier = Modifier.height(40.dp))
            }

            // Time
            MaxWidthHorizontalDivider()
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BodyMedium(
                    text = stringResource(id = R.string.ns_time_utc),
                    color = WalletTheme.colors.secondaryTitleText
                )
                val timeText =
                    overview.blockTimeEpochSeconds?.let {
                        LocalDateTime
                            .ofInstant(
                                java.time.Instant.ofEpochSecond(it),
                                ZoneOffset.UTC
                            ).toString()
                            .replace("T", " ")
                    } ?: stringResource(id = R.string.ns_transaction_date_error)
                BodyMedium(
                    text = timeText,
                    color = WalletTheme.colors.secondaryTitleText
                )
            }

            // Network
            /* Spacer(modifier = Modifier.height(10.dp))
             MaxWidthHorizontalDivider()
             Spacer(modifier = Modifier.height(10.dp))
             Row(
                 modifier = Modifier.fillMaxWidth(),
                 horizontalArrangement = Arrangement.SpaceBetween
             ) {
                 BodyMedium(
                     text = stringResource(id = R.string.ns_network),
                     color = WalletTheme.colors.secondaryTitleText
                 )
                 BodyMedium(
                     text = transactionDetailsUIModel.network?.networkName ?: "",
                     color = WalletTheme.colors.secondaryTitleText
                 )
             }*/

            // BlockId
            Spacer(modifier = Modifier.height(10.dp))
            MaxWidthHorizontalDivider()
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BodyMedium(
                    text = stringResource(id = R.string.ns_block_id),
                    color = WalletTheme.colors.secondaryTitleText
                )
                BodyMedium(
                    text = "${overview.minedHeight}",
                    color = WalletTheme.colors.secondaryTitleText
                )
            }

            // Confirmations
            val countText = getCountText(transactionDetailsUIModel)
            Spacer(modifier = Modifier.height(10.dp))
            MaxWidthHorizontalDivider()
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BodyMedium(
                    text = stringResource(id = R.string.ns_confirmations),
                    color = WalletTheme.colors.secondaryTitleText
                )
                BodyMedium(text = countText, color = WalletTheme.colors.secondaryTitleText)
            }

            // TransactionId
            Spacer(modifier = Modifier.height(10.dp))
            val transactionId = overview.rawId
            MaxWidthHorizontalDivider()
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BodyMedium(
                    text = stringResource(id = R.string.ns_transaction_id),
                    color = WalletTheme.colors.secondaryTitleText
                )
                Spacer(modifier = Modifier.width(50.dp))
                BodyMedium(
                    text = transactionId,
                    color = WalletTheme.colors.secondaryTitleText,
                    textAlign = TextAlign.End
                )
            }

            val blockExplorerUrl =
                stringResource(
                    id = transactionDetailsUIModel.network.darkfiExplorerUrlStringId(),
                    transactionId,
                )
            val onViewBlockExplorerClicked = { updateWarningStatus: Boolean ->
                viewOnBlockExplorer(
                    blockExplorerUrl,
                    updateWarningStatus,
                )
            }
            TextButton(
                onClick = {
                    if (isNavigateAwayFromAppWarningShown) {
                        onViewBlockExplorerClicked(false)
                    } else {
                        showAppLeavingDialog.value = true
                    }
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                BodyMedium(
                    text = stringResource(id = R.string.ns_view_block_explorer),
                    color = WalletTheme.colors.onBackgroundHeader,
                    textAlign = TextAlign.End,
                    textDecoration = TextDecoration.Underline
                )
            }

            // Recipient
            if (overview.isSentTransaction) {
                val recipientAddress = transactionDetailsUIModel.transactionRecipient?.addressValue.orEmpty()
                if (recipientAddress.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    MaxWidthHorizontalDivider()
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        BodyMedium(
                            text = stringResource(id = R.string.ns_recipient),
                            color = WalletTheme.colors.secondaryTitleText
                        )
                        BodyMedium(
                            text = stringResource(id = recipientAddress.addressTypeNameId()),
                            color = WalletTheme.colors.secondaryTitleText
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        BodyMedium(
                            text = stringResource(id = R.string.ns_address),
                            color = WalletTheme.colors.secondaryTitleText
                        )
                        Spacer(modifier = Modifier.width(50.dp))
                        BodyMedium(
                            text =
                                buildAnnotatedString {
                                    if (recipientAddress.length > 20) {
                                        withStyle(style = SpanStyle(color = Color.White)) {
                                            append(recipientAddress.take(10))
                                        }
                                        withStyle(style = SpanStyle(color = WalletTheme.colors.secondaryTitleText)) {
                                            append(
                                                recipientAddress.substring(
                                                    10,
                                                    recipientAddress.length - 10
                                                )
                                            )
                                        }
                                        withStyle(style = SpanStyle(color = Color.White)) {
                                            append(recipientAddress.takeLast(10))
                                        }
                                    } else {
                                        withStyle(style = SpanStyle(color = WalletTheme.colors.secondaryTitleText)) {
                                            append(recipientAddress)
                                        }
                                    }
                                },
                            textAlign = TextAlign.End
                        )
                    }
                }
            }

            // Sub total
            Spacer(modifier = Modifier.height(10.dp))
            MaxWidthHorizontalDivider()
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BodyMedium(
                    text = stringResource(id = R.string.ns_subtotal),
                    color = WalletTheme.colors.secondaryTitleText
                )
                BodyMedium(
                    text =
                        DarkfiAmountFormatter
                            .formatAtomic(
                                (overview.netValueAtomic - overview.totalFeeAtomic).coerceAtLeast(0L),
                            ).removeTrailingZero() + " " + "DRK",
                    color = WalletTheme.colors.secondaryTitleText
                )
            }
            // Fees paid
            if (overview.totalFeeAtomic > 0L) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    BodyMedium(
                        text = stringResource(id = R.string.ns_network_fee),
                        color = WalletTheme.colors.surfaceEnd
                    )
                    Spacer(modifier = Modifier.width(50.dp))
                    BodyMedium(
                        text = "${
                            DarkfiAmountFormatter.formatAtomic(overview.totalFeeAtomic).removeTrailingZero()
                        } DRK",
                        color = WalletTheme.colors.secondaryTitleText,
                        textAlign = TextAlign.End
                    )
                }
            }

            // Sync method used to discover/build this transaction (shared Rust
            // `SyncMethod` via UniFFI). Hidden when unknown/legacy.
            if (overview.syncMethod != DarkfiSyncMethod.UNKNOWN) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    BodyMedium(
                        text = stringResource(id = R.string.ns_sync_method),
                        color = WalletTheme.colors.surfaceEnd
                    )
                    Spacer(modifier = Modifier.width(50.dp))
                    BodyMedium(
                        text = overview.syncMethod.displayLabel,
                        color = WalletTheme.colors.secondaryTitleText,
                        textAlign = TextAlign.End
                    )
                }
            }

            // Total
            Spacer(modifier = Modifier.height(10.dp))
            MaxWidthHorizontalDivider()
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BodyMedium(
                    text = stringResource(id = R.string.ns_total_amount),
                    color = WalletTheme.colors.secondaryTitleText
                )
                BodyMedium(
                    text = "${
                        DarkfiAmountFormatter.formatAtomic(overview.netValueAtomic)
                            .removeTrailingZero()
                    } DRK",
                    color = WalletTheme.colors.secondaryTitleText
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            MaxWidthHorizontalDivider()

            if (showAppLeavingDialog.value) {
                AlertDialog(
                    title = stringResource(id = R.string.dialog_first_use_view_tx_title),
                    desc = stringResource(id = R.string.dialog_first_use_view_tx_message),
                    confirmText = stringResource(id = R.string.dialog_first_use_view_tx_positive),
                    dismissText = stringResource(id = R.string.ns_cancel),
                    onConfirm = {
                        onViewBlockExplorerClicked(true)
                        showAppLeavingDialog.value = false
                    },
                    onDismiss = {
                        showAppLeavingDialog.value = false
                    }
                )
            }
        }
    }
}

private fun getCountText(transactionDetailsUIModel: TransactionDetailsUIModel): String {
    val latestBlockHeight = transactionDetailsUIModel.networkHeight
    val minedHeight = transactionDetailsUIModel.transactionOverview?.minedHeight
    return when {
        latestBlockHeight != null && minedHeight != null -> "${latestBlockHeight - minedHeight}"
        minedHeight != null -> "Confirmed"
        else -> "Pending"
    }
}
