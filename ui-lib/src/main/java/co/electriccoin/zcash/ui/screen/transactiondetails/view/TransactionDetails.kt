package co.electriccoin.zcash.ui.screen.transactiondetails.view

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
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
import cash.z.ecc.android.sdk.fixture.TransactionOverviewFixture
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.TransactionRecipient
import cash.z.ecc.android.sdk.model.TransactionState
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.ZcashNetwork
import cash.z.ecc.android.sdk.model.toZecString
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.AlertDialog
import co.electriccoin.zcash.ui.common.addressTypeNameId
import co.electriccoin.zcash.ui.common.blockExplorerUrlStringId
import co.electriccoin.zcash.ui.common.removeTrailingZero
import co.electriccoin.zcash.ui.common.toFormattedString
import co.electriccoin.zcash.ui.design.component.BalanceText
import co.electriccoin.zcash.ui.design.component.BodyMedium
import co.electriccoin.zcash.ui.design.component.DottedBorderTextButton
import co.electriccoin.zcash.ui.design.component.MaxWidthHorizontalDivider
import co.electriccoin.zcash.ui.design.component.TitleLarge
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.screen.transactiondetails.model.TransactionDetailsUIModel
import co.electriccoin.zcash.ui.screen.wallet.model.BalanceUIModel
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Preview
@Composable
fun TransactionDetailsPreview() {
    ZcashTheme(darkTheme = false) {
        Surface {
            val transactionDetailsUIModel = TransactionDetailsUIModel(
                transactionOverview = TransactionOverviewFixture.new(),
                transactionRecipient = TransactionRecipient.Address("jhasdgjhagsdjagsjadjhgasjhdgajshdgjahsgdjasgdjasgdjsad"),
                network = ZcashNetwork.Mainnet,
                networkHeight = BlockHeight.new(
                    blockHeight = ZcashNetwork.Mainnet.saplingActivationHeight.value + 10
                )
            )
            TransactionDetails(
                transactionDetailsUIModel = transactionDetailsUIModel,
                balanceUIModel = BalanceUIModel("50", "ZEC", "154.92", "USD"),
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
    ZcashTheme(darkTheme = true) {
        Surface {
            val transactionDetailsUIModel = TransactionDetailsUIModel(
                transactionOverview = TransactionOverviewFixture.new(),
                transactionRecipient = TransactionRecipient.Address("jhasdgjhagsdjagsjadjhgasjhdgajshdgjahsgdjasgdjasgdjsad"),
                network = ZcashNetwork.Mainnet,
                networkHeight = BlockHeight.new(
                    blockHeight = ZcashNetwork.Mainnet.saplingActivationHeight.value + 10
                )
            )
            TransactionDetails(
                transactionDetailsUIModel = transactionDetailsUIModel,
                balanceUIModel = BalanceUIModel("50", "ZEC", "154.92", "USD"),
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
        modifier = Modifier
            .fillMaxSize()
            .padding(dimensionResource(id = R.dimen.screen_standard_margin))
            .verticalScroll(rememberScrollState())
    ) {
        val context = LocalContext.current
        val showAppLeavingDialog = remember {
            mutableStateOf(false)
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier.size(dimensionResource(id = R.dimen.back_icon_size))
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = stringResource(R.string.receive_back_content_description)
            )
        }
        Image(
            painter = painterResource(id = R.drawable.ic_nighthawk_logo),
            contentDescription = "logo",
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
            color = ZcashTheme.colors.secondaryTitleText
        )
        Spacer(modifier = Modifier.height(38.dp))

        if (transactionDetailsUIModel?.transactionOverview == null) {
            Twig.debug { "Transaction overview ui model is null" }
            BodyMedium(
                text = stringResource(id = R.string.ns_transaction_details_error_msg),
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                color = ZcashTheme.colors.surfaceEnd
            )
        } else {

            Icon(
                painter = painterResource(id = R.drawable.ic_icon_downloading),
                contentDescription = null,
                modifier = Modifier
                    .rotate(if (transactionDetailsUIModel.transactionOverview.isSentTransaction) 180f else 0f)
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
                color = ZcashTheme.colors.secondaryTitleText
            )
            Spacer(
                modifier = Modifier.height(30.dp)
            )

            val (transactionStateTextId, transactionStateIconId) = when (transactionDetailsUIModel.transactionOverview.transactionState) {
                TransactionState.Confirmed -> Pair(
                    R.string.ns_confirmed,
                    R.drawable.ic_icon_confirmed
                )

                TransactionState.Pending -> Pair(R.string.ns_pending, R.drawable.ic_icon_preparing)
                TransactionState.Expired -> Pair(R.string.ns_expired, R.drawable.ic_done_24dp)
            }

            DottedBorderTextButton(
                onClick = {},
                text = stringResource(id = transactionStateTextId),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .height(40.dp),
                borderColor = ZcashTheme.colors.secondaryTitleText,
                startIcon = transactionStateIconId
            )

            Spacer(
                modifier = Modifier.heightIn(min = 50.dp)
            )

            // Memo
            if (transactionDetailsUIModel.memo.isNotBlank()) {
                BodyMedium(
                    text = stringResource(id = R.string.ns_memo),
                    color = ZcashTheme.colors.secondaryTitleText
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
                    color = ZcashTheme.colors.secondaryTitleText
                )
                val timeText = transactionDetailsUIModel.transactionOverview.blockTimeEpochSeconds?.let {
                    Instant.fromEpochSeconds(it).toLocalDateTime(TimeZone.UTC).toString().replace("T", " ")
                } ?: stringResource(id = R.string.ns_transaction_date_error)
                BodyMedium(
                    text = timeText,
                    color = ZcashTheme.colors.secondaryTitleText
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
                    color = ZcashTheme.colors.secondaryTitleText
                )
                BodyMedium(
                    text = transactionDetailsUIModel.network?.networkName ?: "",
                    color = ZcashTheme.colors.secondaryTitleText
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
                    color = ZcashTheme.colors.secondaryTitleText
                )
                BodyMedium(
                    text = "${transactionDetailsUIModel.transactionOverview.minedHeight?.value}",
                    color = ZcashTheme.colors.secondaryTitleText
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
                    color = ZcashTheme.colors.secondaryTitleText
                )
                BodyMedium(text = countText, color = ZcashTheme.colors.secondaryTitleText)
            }

            // TransactionId
            Spacer(modifier = Modifier.height(10.dp))
            val transactionId =
                transactionDetailsUIModel.transactionOverview.rawId.byteArray.toFormattedString()
            MaxWidthHorizontalDivider()
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BodyMedium(
                    text = stringResource(id = R.string.ns_transaction_id),
                    color = ZcashTheme.colors.secondaryTitleText
                )
                Spacer(modifier = Modifier.width(50.dp))
                BodyMedium(
                    text = transactionId,
                    color = ZcashTheme.colors.secondaryTitleText,
                    textAlign = TextAlign.End
                )
            }

            val onViewBlockExplorerClicked = { updateWarningStatus: Boolean ->
                viewOnBlockExplorer(
                    context.getString(
                        transactionDetailsUIModel.network.blockExplorerUrlStringId(),
                        transactionId
                    ),
                    updateWarningStatus
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
                    color = ZcashTheme.colors.onBackgroundHeader,
                    textAlign = TextAlign.End,
                    textDecoration = TextDecoration.Underline
                )
            }

            // Recipient
            if (transactionDetailsUIModel.transactionOverview.isSentTransaction) {
                val recipientAddress = when (transactionDetailsUIModel.transactionRecipient) {
                    is TransactionRecipient.Address -> transactionDetailsUIModel.transactionRecipient.addressValue
                    else -> ""
                }
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
                            color = ZcashTheme.colors.secondaryTitleText
                        )
                        BodyMedium(
                            text = stringResource(id = recipientAddress.addressTypeNameId()),
                            color = ZcashTheme.colors.secondaryTitleText
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        BodyMedium(
                            text = stringResource(id = R.string.ns_address),
                            color = ZcashTheme.colors.secondaryTitleText
                        )
                        Spacer(modifier = Modifier.width(50.dp))
                        BodyMedium(
                            text = buildAnnotatedString {
                                if (recipientAddress.length > 20) {
                                    withStyle(style = SpanStyle(color = Color.White)) {
                                        append(recipientAddress.take(10))
                                    }
                                    withStyle(style = SpanStyle(color = ZcashTheme.colors.secondaryTitleText)) {
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
                                    withStyle(style = SpanStyle(color = ZcashTheme.colors.secondaryTitleText)) {
                                        append(recipientAddress)
                                    }
                                }
                            },
                            textAlign = TextAlign.End
                        )
                    }
                }
            }

            //Sub total
            Spacer(modifier = Modifier.height(10.dp))
            MaxWidthHorizontalDivider()
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BodyMedium(
                    text = stringResource(id = R.string.ns_subtotal),
                    color = ZcashTheme.colors.secondaryTitleText
                )
                BodyMedium(
                    text = (transactionDetailsUIModel.transactionOverview.netValue - (transactionDetailsUIModel.transactionOverview.feePaid
                        ?: Zatoshi(0))).toZecString().removeTrailingZero() + " " + stringResource(
                        id = R.string.ns_zec
                    ), color = ZcashTheme.colors.secondaryTitleText
                )
            }
            // Fees paid
            transactionDetailsUIModel.transactionOverview.feePaid?.let {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    BodyMedium(
                        text = stringResource(id = R.string.ns_network_fee),
                        color = ZcashTheme.colors.surfaceEnd
                    )
                    Spacer(modifier = Modifier.width(50.dp))
                    BodyMedium(
                        text = "${
                            it.toZecString().removeTrailingZero()
                        } ${stringResource(id = R.string.ns_zec)}",
                        color = ZcashTheme.colors.secondaryTitleText, textAlign = TextAlign.End
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
                    color = ZcashTheme.colors.secondaryTitleText
                )
                BodyMedium(
                    text = "${
                        (transactionDetailsUIModel.transactionOverview.netValue)
                            .toZecString()
                            .removeTrailingZero()
                    } ${stringResource(id = R.string.ns_zec)}",
                    color = ZcashTheme.colors.secondaryTitleText
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
    return if (latestBlockHeight == null) {
        if (isSufficientlyOld(transactionDetailsUIModel)) "Confirmed" else "Transaction Count unavailable"
    } else if (minedHeight != null) {
        "${latestBlockHeight.value - minedHeight.value}"
    } else if ((transactionDetailsUIModel.transactionOverview?.expiryHeight?.value
            ?: Long.MAX_VALUE) < latestBlockHeight.value
    ) {
        "Pending"
    } else {
        "Expired"
    }
}

private fun isSufficientlyOld(tx: TransactionDetailsUIModel): Boolean {
    val threshold = 75 * 1000 * 25 // approx 25 blocks
    val delta = System.currentTimeMillis() / 1000L - (tx.transactionOverview?.blockTimeEpochSeconds
        ?: threshold.toLong())
    return (tx.transactionOverview?.minedHeight?.value
        ?: Long.MIN_VALUE) > (tx.network?.saplingActivationHeight?.value ?: Long.MIN_VALUE) &&
            delta < threshold
}
