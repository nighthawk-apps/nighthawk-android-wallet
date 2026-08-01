package com.nighthawkapps.lib.android.ui.screen.receive.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.BrightenScreen
import com.nighthawkapps.lib.android.ui.common.DisableScreenTimeout
import com.nighthawkapps.lib.android.ui.design.MINIMAL_WEIGHT
import com.nighthawkapps.lib.android.ui.design.component.GradientSurface
import com.nighthawkapps.lib.android.ui.design.component.Header
import com.nighthawkapps.lib.android.ui.design.component.PrimaryButton
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme
import com.nighthawkapps.lib.android.ui.screen.receive.util.AndroidQrCodeImageGenerator
import com.nighthawkapps.lib.android.ui.screen.receive.util.JvmQrCodeGenerator
import kotlin.math.roundToInt

@Preview("Receive")
@Composable
private fun ComposablePreview() {
    WalletTheme(darkTheme = true) {
        GradientSurface {
            Receive(
                depositUriPrimary = "darkfi_preview_deposit_uri",
                onBack = {},
                onAddressDetails = {},
            )
        }
    }
}

@Composable
@Suppress("LongParameterList")
fun Receive(
    depositUriPrimary: String,
    onBack: () -> Unit,
    onAddressDetails: () -> Unit,
) {
    Column {
        ReceiveTopAppBar(onBack = onBack)
        ReceiveContents(
            depositUriPrimary = depositUriPrimary,
            onAddressDetails = onAddressDetails,
            modifier =
                Modifier
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(all = WalletTheme.dimens.spacingDefault)
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ReceiveTopAppBar(onBack: () -> Unit) {
    TopAppBar(
        title = { Text(text = stringResource(id = R.string.receive_title)) },
        navigationIcon = {
            IconButton(
                onClick = onBack
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.receive_back_content_description)
                )
            }
        }
    )
}

private val DEFAULT_QR_CODE_SIZE = 320.dp

/** Keeps URI prefix/suffix visible when the deposit string exceeds one-line width expectations. */
@Suppress("MagicNumber")
private fun middleEllipsizeDepositUri(text: String): String {
    val maxChars = 44
    if (text.length <= maxChars) return text
    val ellipsis = '\u2026'
    val innerBudget = maxChars - 1
    val headLen = innerBudget / 2
    val tailLen = innerBudget - headLen
    return "${text.take(headLen)}$ellipsis${text.takeLast(tailLen)}"
}

@Composable
@Suppress("LongParameterList")
private fun ReceiveContents(
    depositUriPrimary: String,
    onAddressDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        QrCode(
            data = depositUriPrimary,
            DEFAULT_QR_CODE_SIZE,
            Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(WalletTheme.dimens.spacingLarge))

        Header(
            text = stringResource(id = R.string.wallet_address_primary_deposit),
            Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(WalletTheme.dimens.spacingSmall))

        Text(
            text = middleEllipsizeDepositUri(depositUriPrimary),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            overflow = TextOverflow.Ellipsis,
            maxLines = 1
        )

        Spacer(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .weight(MINIMAL_WEIGHT)
        )

        PrimaryButton(
            onClick = onAddressDetails,
            text = stringResource(id = R.string.receive_see_address_details),
            outerPaddingValues = PaddingValues(all = WalletTheme.dimens.spacingNone)
        )
    }
}

@Composable
private fun QrCode(
    data: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        BrightenScreen()
        DisableScreenTimeout()
        val sizePixels = with(LocalDensity.current) { size.toPx() }.roundToInt()

        // In the future, use actual/expect to switch QR code generator implementations for multiplatform

        // Note that our implementation has an extra array copy to BooleanArray, which is a cross-platform
        // representation.  This should have minimal performance impact since the QR code is relatively
        // small and we only generate QR codes infrequently.

        val qrCodePixelArray = JvmQrCodeGenerator.generate(data, sizePixels)
        val qrCodeImage = AndroidQrCodeImageGenerator.generate(qrCodePixelArray, sizePixels)

        Image(
            bitmap = qrCodeImage,
            contentDescription = stringResource(R.string.receive_qr_code_content_description)
        )
    }
}
