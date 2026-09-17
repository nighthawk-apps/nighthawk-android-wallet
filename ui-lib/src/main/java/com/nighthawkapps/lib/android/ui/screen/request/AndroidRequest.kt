@file:Suppress("ktlint:standard:filename")

package com.nighthawkapps.lib.android.ui.screen.request

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nighthawkapps.lib.android.global.DeepLinkUtil
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.BrightenScreen
import com.nighthawkapps.lib.android.ui.common.DisableScreenTimeout
import com.nighthawkapps.lib.android.ui.common.setPlainText
import com.nighthawkapps.lib.android.ui.design.component.BodyMedium
import com.nighthawkapps.lib.android.ui.design.component.BodySmall
import com.nighthawkapps.lib.android.ui.design.component.PrimaryButton
import com.nighthawkapps.lib.android.ui.design.component.TitleLarge
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.WalletViewModel
import com.nighthawkapps.lib.android.ui.screen.receive.util.AndroidQrCodeImageGenerator
import com.nighthawkapps.lib.android.ui.screen.receive.util.JvmQrCodeGenerator
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun MainActivity.WrapRequest(goBack: () -> Unit) {
    val walletViewModel by viewModels<WalletViewModel>()
    val addresses = walletViewModel.addresses.collectAsStateWithLifecycle().value
    val address = addresses?.privateAddresses?.firstOrNull().orEmpty()
    RequestMoneyScreen(
        receiveAddress = address,
        onBack = goBack,
    )
}

@Composable
internal fun RequestMoneyScreen(
    receiveAddress: String,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
    val context = LocalContext.current
    var amountText by remember { mutableStateOf("") }
    var memoText by remember { mutableStateOf("") }
    val uri =
        remember(receiveAddress, amountText, memoText) {
            DeepLinkUtil.buildPaymentRequestUri(
                address = receiveAddress,
                amountDisplay = amountText.ifBlank { null },
                memo = memoText.ifBlank { null },
            )
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(dimensionResource(id = R.dimen.screen_standard_margin))
                .verticalScroll(rememberScrollState()),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(dimensionResource(id = R.dimen.back_icon_size)),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.receive_back_content_description),
            )
        }
        Image(
            painter = painterResource(id = R.drawable.ic_nighthawk_logo),
            contentDescription = stringResource(id = R.string.ns_logo_desc),
            contentScale = ContentScale.Inside,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(dimensionResource(id = R.dimen.pageMargin)))
        TitleLarge(
            text = stringResource(id = R.string.ns_request_money),
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(modifier = Modifier.height(8.dp))
        BodyMedium(
            text = stringResource(id = R.string.ns_request_single_address),
            textAlign = TextAlign.Center,
            color = WalletTheme.colors.secondaryTitleText,
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { BodyMedium(text = stringResource(R.string.ns_request_amount_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = memoText,
            onValueChange = { memoText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { BodyMedium(text = stringResource(R.string.ns_request_memo_hint)) },
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (!uri.isNullOrBlank()) {
            RequestQr(data = uri, logoId = R.drawable.ic_icon_biometric, backGroundColor = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))
            BodySmall(text = uri)
            Spacer(modifier = Modifier.height(16.dp))
            PrimaryButton(
                onClick = {
                    clipboardScope.launch { clipboard.setPlainText(uri) }
                    Toast.makeText(context, R.string.ns_copied, Toast.LENGTH_SHORT).show()
                },
                text = stringResource(id = R.string.ns_request_copy_uri),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        } else {
            BodyMedium(
                text = stringResource(id = R.string.ns_request_need_address),
                color = WalletTheme.colors.secondaryTitleText,
            )
        }
    }
}

@Composable
private fun RequestQr(
    data: String,
    @DrawableRes logoId: Int,
    backGroundColor: Color,
) {
    val size = 180.dp
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        BrightenScreen()
        DisableScreenTimeout()
        val sizePixels = with(LocalDensity.current) { size.toPx() }.roundToInt()
        val qrCodePixelArray = JvmQrCodeGenerator.generate(data, sizePixels)
        val qrCodeImage = AndroidQrCodeImageGenerator.generate(qrCodePixelArray, sizePixels)
        Image(
            bitmap = qrCodeImage,
            contentDescription = stringResource(R.string.receive_qr_code_content_description),
        )
        Box(
            modifier =
                Modifier
                    .size(50.dp)
                    .background(color = backGroundColor, shape = CircleShape)
                    .border(width = 4.dp, shape = CircleShape, color = Color.White),
        ) {
            Image(
                painter = painterResource(id = logoId),
                contentDescription = null,
                modifier =
                    Modifier
                        .size(24.dp)
                        .align(Alignment.Center),
            )
        }
    }
}
