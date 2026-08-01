@file:Suppress("ModifierReused", "LongMethod", "MaxLineLength")

package com.nighthawkapps.lib.android.ui.screen.receiveqrcodes.view

import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiWalletAddresses
import com.nighthawkapps.lib.android.spackle.Twig
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.BrightenScreen
import com.nighthawkapps.lib.android.ui.common.DisableScreenTimeout
import com.nighthawkapps.lib.android.ui.common.setPlainText
import com.nighthawkapps.lib.android.ui.design.component.BodyMedium
import com.nighthawkapps.lib.android.ui.design.component.BodySmall
import com.nighthawkapps.lib.android.ui.design.component.PrimaryButton
import com.nighthawkapps.lib.android.ui.design.component.TitleLarge
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme
import com.nighthawkapps.lib.android.ui.screen.receive.util.AndroidQrCodeImageGenerator
import com.nighthawkapps.lib.android.ui.screen.receive.util.JvmQrCodeGenerator
import com.nighthawkapps.lib.android.ui.screen.receiveqrcodes.QRAddressPagerItem
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
@Preview
fun ReceiveQrCodesPreview() {
    WalletTheme(darkTheme = false) {
        Surface {
            ReceiveQrCodes(
                walletAddresses =
                    DarkfiWalletAddresses(
                        privateAddresses = listOf("preview_default_address"),
                    ),
                onBack = {},
                onSeeMoreTopUpOption = {},
                onCreateNewAddress = {}
            )
        }
    }
}

@Composable
@Preview
fun QrAddressCardPreview() {
    WalletTheme(darkTheme = false) {
        Surface {
            QrAddressCardUi(
                QRAddressPagerItem.PRIVATE_ADDRESS(
                    addressType = stringResource(id = R.string.ns_private_address),
                    address = "unikasjdkjdjkjsakdjjkajsdkasdkjasdkjsadkjsakjd,aksdjkjdasjkjsjkasjksa",
                    btnText = stringResource(id = R.string.ns_copy),
                    id = R.drawable.ic_icon_biometric,
                    backGroundColor = MaterialTheme.colorScheme.primary
                ),
                onCopyAddress = {}
            )
        }
    }
}

private val DEFAULT_QR_CODE_SIZE = 180.dp
private const val QR_CARD_HEIGHT_PER = 0.8f

@Composable
fun ReceiveQrCodes(
    walletAddresses: DarkfiWalletAddresses,
    onBack: () -> Unit,
    onSeeMoreTopUpOption: () -> Unit,
    onCreateNewAddress: () -> Unit
) {
    Twig.debug { "WalletAddresses $walletAddresses" }
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.ns_copied)
    val defaultAddress = walletAddresses.privateAddresses.firstOrNull().orEmpty()
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(dimensionResource(id = R.dimen.screen_standard_margin))
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(dimensionResource(id = R.dimen.back_icon_size))
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.receive_back_content_description)
            )
        }
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
        Spacer(modifier = Modifier.height(27.dp))

        // Single default address card — no pager
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(QR_CARD_HEIGHT_PER)
                    .padding(horizontal = 32.dp)
                    .align(Alignment.CenterHorizontally),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = WalletTheme.colors.navigationContainer)
        ) {
            QrAddressCardUi(
                qrAddressPagerItem =
                    QRAddressPagerItem.PRIVATE_ADDRESS(
                        addressType = stringResource(id = R.string.ns_private_address),
                        address = defaultAddress,
                        btnText = stringResource(id = R.string.ns_copy),
                        id = R.drawable.ic_icon_biometric,
                        backGroundColor = MaterialTheme.colorScheme.primary
                    ),
                onCopyAddress = {
                    clipboardScope.launch {
                        clipboard.setPlainText(it)
                    }
                    Toast
                        .makeText(
                            context,
                            copiedMessage,
                            Toast.LENGTH_SHORT
                        ).show()
                }
            )
        }
    }
}

@Composable
fun QrAddressCardUi(
    qrAddressPagerItem: QRAddressPagerItem,
    onCopyAddress: (String) -> Unit
) {
    Column(
        modifier =
            Modifier
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
    ) {
        QrCode(
            data = qrAddressPagerItem.body,
            logoId = qrAddressPagerItem.logoId,
            backGroundColor = qrAddressPagerItem.backgroundColor,
            DEFAULT_QR_CODE_SIZE,
            Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(18.dp))
        BodyMedium(
            text = qrAddressPagerItem.title,
            color = colorResource(id = com.nighthawkapps.lib.android.ui.design.R.color.ns_parmaviolet)
        )
        Spacer(modifier = Modifier.height(10.dp))
        BodySmall(text = qrAddressPagerItem.body)
        Spacer(modifier = Modifier.weight(1f))
        PrimaryButton(
            onClick = { onCopyAddress(qrAddressPagerItem.body) },
            text = qrAddressPagerItem.buttonText.uppercase(),
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .sizeIn(
                        minWidth = dimensionResource(id = R.dimen.button_min_width),
                        minHeight = dimensionResource(id = R.dimen.button_height)
                    )
        )
    }
}

@Composable
private fun QrCode(
    data: String,
    @DrawableRes logoId: Int,
    backGroundColor: Color,
    size: Dp,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(modifier = modifier) {
            BrightenScreen()
            DisableScreenTimeout()
            val sizePixels = with(LocalDensity.current) { size.toPx() }.roundToInt()

            if (data.isNotBlank()) {
                val qrCodePixelArray = JvmQrCodeGenerator.generate(data, sizePixels)
                val qrCodeImage = AndroidQrCodeImageGenerator.generate(qrCodePixelArray, sizePixels)

                Image(
                    bitmap = qrCodeImage,
                    contentDescription = stringResource(R.string.receive_qr_code_content_description)
                )
            } else {
                Box(modifier = Modifier.size(size))
            }
        }
        QrLogo(logoId = logoId, backGroundColor = backGroundColor)
    }
}

@Composable
internal fun QrLogo(
    @DrawableRes logoId: Int,
    backGroundColor: Color
) {
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
                    .align(Alignment.Center)
        )
    }
}
