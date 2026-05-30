@file:Suppress("ModifierReused", "LongMethod", "MaxLineLength")

package com.nighthawkapps.lib.android.ui.screen.receiveqrcodes.view

import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.util.lerp
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
import com.nighthawkapps.lib.android.ui.screen.wallet.view.PageIndicator
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@Composable
@Preview
fun ReceiveQrCodesPreview() {
    WalletTheme(darkTheme = false) {
        Surface {
            ReceiveQrCodes(
                walletAddresses =
                    DarkfiWalletAddresses(
                        privateAddresses = listOf("preview_private", "preview_private_2"),
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

@Composable
@Preview
fun TopUpCardPreview() {
    WalletTheme(darkTheme = false) {
        Surface {
            TopUpCardUi(
                QRAddressPagerItem.TOP_UP(
                    titleText = stringResource(id = R.string.ns_top_up_your_wallet),
                    bodyText = stringResource(id = R.string.ns_top_up_your_wallet_msg),
                    btnText = stringResource(id = R.string.ns_see_more),
                    id = R.drawable.ic_icon_top_up,
                    backGroundColor = Color.Transparent
                ),
                onSeeMore = {}
            )
        }
    }
}

private val DEFAULT_QR_CODE_SIZE = 180.dp
private const val QR_CARD_HEIGHT_PER = 0.8f

@OptIn(ExperimentalFoundationApi::class)
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

        val totalCards = walletAddresses.privateAddresses.size + 2
        val state =
            rememberPagerState(initialPage = 0, initialPageOffsetFraction = 0f) { totalCards }
        HorizontalPager(
            state = state,
            pageSpacing = 16.dp,
            contentPadding = PaddingValues(horizontal = 55.dp)
        ) { page ->
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(QR_CARD_HEIGHT_PER)
                        .align(Alignment.CenterHorizontally)
                        .graphicsLayer {
                            val pageOffset =
                                ((state.currentPage - page) + state.currentPageOffsetFraction).absoluteValue
                            // We animate the alpha, between 50% and 100%
                            val animOffset =
                                lerp(
                                    start = 0.8f,
                                    stop = 1f,
                                    fraction = 1f - pageOffset.coerceIn(0f, 1f)
                                )
                            scaleY = animOffset
                            alpha = animOffset
                        },
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(containerColor = WalletTheme.colors.navigationContainer)
            ) {
                val qrAddressPagerItem =
                    getQRAddressPagerItem(page = page, walletAddresses = walletAddresses)
                if (qrAddressPagerItem is QRAddressPagerItem.TOP_UP) {
                    TopUpCardUi(topUp = qrAddressPagerItem, onSeeMore = onSeeMoreTopUpOption)
                } else if (qrAddressPagerItem is QRAddressPagerItem.CREATE_NEW_ADDRESS) {
                    TopUpCardUi(
                        topUp =
                            QRAddressPagerItem.TOP_UP(
                                titleText = qrAddressPagerItem.title,
                                bodyText = qrAddressPagerItem.body,
                                btnText = qrAddressPagerItem.buttonText,
                                id = qrAddressPagerItem.logoId,
                                backGroundColor = qrAddressPagerItem.backgroundColor
                            ),
                        onSeeMore = onCreateNewAddress
                    )
                } else {
                    QrAddressCardUi(
                        qrAddressPagerItem = qrAddressPagerItem,
                        onCopyAddress = {
                            clipboardScope.launch {
                                clipboard.setPlainText(it)
                            }
                            Toast
                                .makeText(
                                    context,
                                    context.getString(R.string.ns_copied),
                                    Toast.LENGTH_SHORT
                                ).show()
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(27.dp))
        PageIndicator(pageCount = totalCards, pagerState = state)
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
fun TopUpCardUi(
    topUp: QRAddressPagerItem.TOP_UP,
    onSeeMore: () -> Unit
) {
    Column(
        modifier =
            Modifier
                .padding(16.dp)
                .fillMaxWidth()
    ) {
        Icon(
            painter = painterResource(id = topUp.logoId),
            contentDescription = null,
            tint = colorResource(id = com.nighthawkapps.lib.android.ui.design.R.color.ns_parmaviolet)
        )
        Spacer(modifier = Modifier.height(21.dp))
        BodyMedium(
            text = topUp.title,
            color = colorResource(id = com.nighthawkapps.lib.android.ui.design.R.color.ns_parmaviolet)
        )
        Spacer(modifier = Modifier.height(10.dp))
        BodySmall(text = topUp.body)
        Spacer(modifier = Modifier.weight(1f))
        PrimaryButton(
            onClick = onSeeMore,
            text = topUp.buttonText.uppercase(),
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

@Composable
private fun getQRAddressPagerItem(
    page: Int,
    walletAddresses: DarkfiWalletAddresses
): QRAddressPagerItem {
    val totalAddresses = walletAddresses.privateAddresses.size
    return when {
        page < totalAddresses -> {
            QRAddressPagerItem.PRIVATE_ADDRESS(
                addressType = stringResource(id = R.string.ns_private_address) + " ${page + 1}",
                address = walletAddresses.privateAddresses[page],
                btnText = stringResource(id = R.string.ns_copy),
                id = R.drawable.ic_icon_biometric,
                backGroundColor = MaterialTheme.colorScheme.primary
            )
        }

        page == totalAddresses -> {
            QRAddressPagerItem.CREATE_NEW_ADDRESS(
                titleText = stringResource(id = R.string.ns_create_new_address),
                bodyText = "Generate a new private address for receiving funds. Your existing addresses will continue to work.",
                btnText = "Create Address",
                id = R.drawable.ic_icon_biometric,
                backGroundColor = Color.Transparent
            )
        }

        else -> {
            QRAddressPagerItem.TOP_UP(
                titleText = stringResource(id = R.string.ns_top_up_your_wallet),
                bodyText = stringResource(id = R.string.ns_top_up_your_wallet_msg),
                btnText = stringResource(id = R.string.ns_see_more),
                id = R.drawable.ic_icon_top_up,
                backGroundColor = Color.Transparent
            )
        }
    }
}
