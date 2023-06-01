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
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.z.ecc.android.sdk.fixture.TransactionOverviewFixture
import cash.z.ecc.android.sdk.model.TransactionOverview
import cash.z.ecc.android.sdk.model.TransactionState
import cash.z.ecc.android.sdk.model.toZecString
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BalanceText
import co.electriccoin.zcash.ui.design.component.Body
import co.electriccoin.zcash.ui.design.component.BodyMedium
import co.electriccoin.zcash.ui.design.component.DottedBorderTextButton
import co.electriccoin.zcash.ui.design.component.TitleLarge
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.nio.charset.Charset

@Preview
@Composable
fun TransactionDetailsPreview() {
    ZcashTheme(darkTheme = false) {
        Surface {
            TransactionDetails(transactionOverview = TransactionOverviewFixture.new(), onBack = {})
        }
    }
}

@Composable
fun TransactionDetails(transactionOverview: TransactionOverview?, onBack: () -> Unit) {
    Column(modifier = Modifier
        .fillMaxSize()
        .padding(dimensionResource(id = R.dimen.screen_standard_margin))
        .verticalScroll(rememberScrollState())
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(dimensionResource(id = R.dimen.back_icon_size))) {
            Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.receive_back_content_description))
        }
        Image(painter = painterResource(id = R.drawable.ic_nighthawk_logo), contentDescription = "logo", contentScale = ContentScale.Inside, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(dimensionResource(id = R.dimen.pageMargin)))
        TitleLarge(text = stringResource(id = R.string.ns_nighthawk), textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.pageMargin)))
        BodyMedium(text = stringResource(id = R.string.ns_transaction_details), textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.CenterHorizontally), color = ZcashTheme.colors.surfaceEnd)
        Spacer(modifier = Modifier.height(38.dp))

        if (transactionOverview == null) {
            // May be we can show error dialog or loading
            Twig.info { "Transaction overview is null" }
            return@Column
        }

        Icon(
            painter = painterResource(id = R.drawable.ic_icon_downloading),
            contentDescription = null,
            modifier = Modifier
                .rotate(if (transactionOverview.isSentTransaction) 180f else 0f)
                .align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(21.dp))

        // Amount section
        Row(
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            BalanceText(text = transactionOverview.netValue.toZecString())
            Spacer(modifier = Modifier.width(4.dp))
            BalanceText(text = stringResource(id = R.string.ns_zec), color = ZcashTheme.colors.surfaceEnd)
        }
        Spacer(modifier = Modifier.width(12.dp))
        BodyMedium(text = stringResource(id = R.string.ns_around, "--"), textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.CenterHorizontally), color = ZcashTheme.colors.surfaceEnd)
        Spacer(
            modifier = Modifier.height(30.dp)
        )

        val (transactionStateTextId, transactionStateIconId) = when (transactionOverview.transactionState) {
            TransactionState.Confirmed -> Pair(R.string.ns_confirmed, R.drawable.ic_icon_confirmed)
            TransactionState.Pending -> Pair(R.string.ns_pending, R.drawable.ic_icon_preparing)
            TransactionState.Expired -> Pair(R.string.ns_expired, R.drawable.ic_done_24dp)
        }

        DottedBorderTextButton(
            onClick = {},
            text = stringResource(id = transactionStateTextId),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .height(40.dp),
            borderColor = ZcashTheme.colors.surfaceEnd,
            startIcon = transactionStateIconId
        )

        Spacer(
            modifier = Modifier.heightIn(min = 50.dp)
        )

        // Memo
        if (transactionOverview.memoCount >= 0) { // Todo: change this no check to 1
            BodyMedium(text = stringResource(id = R.string.ns_memo), color = ZcashTheme.colors.surfaceEnd)
            Spacer(modifier = Modifier.height(10.dp))
            Body(text = "memo need to get from synchronizer")
            Spacer(modifier = Modifier.height(40.dp))
        }

        // Time
        Divider(
            thickness = 1.dp,
            color = ZcashTheme.colors.surfaceEnd
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BodyMedium(text = stringResource(id = R.string.ns_time_utc), color = ZcashTheme.colors.surfaceEnd)
            BodyMedium(
                text = Instant.fromEpochSeconds(transactionOverview.blockTimeEpochSeconds).toLocalDateTime(TimeZone.UTC).toString().replace("T", " "),
                color = ZcashTheme.colors.surfaceEnd
            )
        }

        // Network
        Spacer(modifier = Modifier.height(10.dp))
        Divider(
            thickness = 1.dp,
            color = ZcashTheme.colors.surfaceEnd
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BodyMedium(text = stringResource(id = R.string.ns_network), color = ZcashTheme.colors.surfaceEnd)
            BodyMedium(text = "Orchard", color = ZcashTheme.colors.surfaceEnd)
        }

        // BlockId
        Spacer(modifier = Modifier.height(10.dp))
        Divider(
            thickness = 1.dp,
            color = ZcashTheme.colors.surfaceEnd
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BodyMedium(text = stringResource(id = R.string.ns_block_id), color = ZcashTheme.colors.surfaceEnd)
            BodyMedium(text = "--", color = ZcashTheme.colors.surfaceEnd)
        }

        // Confirmations
        Spacer(modifier = Modifier.height(10.dp))
        Divider(
            thickness = 1.dp,
            color = ZcashTheme.colors.surfaceEnd
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BodyMedium(text = stringResource(id = R.string.ns_confirmations), color = ZcashTheme.colors.surfaceEnd)
            BodyMedium(text = "--", color = ZcashTheme.colors.surfaceEnd)
        }

        // TransactionId
        Spacer(modifier = Modifier.height(10.dp))
        Divider(
            thickness = 1.dp,
            color = ZcashTheme.colors.surfaceEnd
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BodyMedium(text = stringResource(id = R.string.ns_transaction_id), color = ZcashTheme.colors.surfaceEnd)
            Spacer(modifier = Modifier.width(50.dp))
            BodyMedium(text = transactionOverview.rawId.byteArray.toString(Charset.defaultCharset()), color = ZcashTheme.colors.surfaceEnd, textAlign = TextAlign.End)
        }
        TextButton(onClick = {},
            modifier = Modifier.align(Alignment.End)) {
            BodyMedium(text = stringResource(id = R.string.ns_view_block_explorer), color = ZcashTheme.colors.onBackgroundHeader, textAlign = TextAlign.End)
        }

        // Recipient
        Spacer(modifier = Modifier.height(10.dp))
        Divider(
            thickness = 1.dp,
            color = ZcashTheme.colors.surfaceEnd
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BodyMedium(text = stringResource(id = R.string.ns_recipient), color = ZcashTheme.colors.surfaceEnd)
            BodyMedium(text = "--", color = ZcashTheme.colors.surfaceEnd)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BodyMedium(text = stringResource(id = R.string.ns_address), color = ZcashTheme.colors.surfaceEnd)
            Spacer(modifier = Modifier.width(50.dp))
            val receiverAddress = "jhgdajhdkjahsdasdhkjashdjahsdjhasjdhkjahdskjhkashdkaskdhkasdh"
            BodyMedium(
                text = buildAnnotatedString {
                    if (receiverAddress.length > 20) {
                        withStyle(style = SpanStyle(color = Color.White)) {
                            append(receiverAddress.take(10))
                        }
                        withStyle(style = SpanStyle(color = ZcashTheme.colors.surfaceEnd)) {
                            append(receiverAddress.substring(10, receiverAddress.length - 10))
                        }
                        withStyle(style = SpanStyle(color = Color.White)) {
                            append(receiverAddress.takeLast(10))
                        }
                    } else {
                        withStyle(style = SpanStyle(color = ZcashTheme.colors.surfaceEnd)) {
                            append(receiverAddress)
                        }
                    }
                },
                textAlign = TextAlign.End
            )
        }

        //Sub total
        Spacer(modifier = Modifier.height(10.dp))
        Divider(
            thickness = 1.dp,
            color = ZcashTheme.colors.surfaceEnd
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BodyMedium(text = stringResource(id = R.string.ns_subtotal), color = ZcashTheme.colors.surfaceEnd)
            BodyMedium(text = (transactionOverview.netValue - transactionOverview.feePaid).toZecString() + stringResource(id = R.string.ns_zec), color = ZcashTheme.colors.surfaceEnd)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BodyMedium(text = stringResource(id = R.string.ns_network_fee), color = ZcashTheme.colors.surfaceEnd)
            Spacer(modifier = Modifier.width(50.dp))
            BodyMedium(text = (transactionOverview.feePaid).toZecString() + stringResource(id = R.string.ns_zec), color = ZcashTheme.colors.surfaceEnd, textAlign = TextAlign.End)
        }

        // Total
        Spacer(modifier = Modifier.height(10.dp))
        Divider(
            thickness = 1.dp,
            color = ZcashTheme.colors.surfaceEnd
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BodyMedium(text = stringResource(id = R.string.ns_total_amount), color = ZcashTheme.colors.surfaceEnd)
            BodyMedium(text = (transactionOverview.netValue).toZecString() + stringResource(id = R.string.ns_zec), color = ZcashTheme.colors.surfaceEnd)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Divider(
            thickness = 1.dp,
            color = ZcashTheme.colors.surfaceEnd
        )
    }
}
