package com.nighthawkapps.lib.android.ui.screen.send.nighthawk.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.customColors
import com.nighthawkapps.lib.android.ui.common.pastePlainText
import com.nighthawkapps.lib.android.ui.design.component.BodyMedium
import com.nighthawkapps.lib.android.ui.design.component.DottedBorderTextButton
import com.nighthawkapps.lib.android.ui.design.component.PrimaryButton
import com.nighthawkapps.lib.android.ui.design.component.TitleLarge
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme
import kotlinx.coroutines.launch

@Composable
@Preview
fun EnterReceiverAddressPreview() {
    WalletTheme(darkTheme = false) {
        Surface {
            EnterReceiverAddress(
                receiverAddress = "",
                isContinueBtnEnabled = false,
                onBack = {},
                onValueChanged = {},
                onContinue = {},
                onScan = {}
            )
        }
    }
}

@Composable
fun EnterReceiverAddress(
    receiverAddress: String,
    isContinueBtnEnabled: Boolean,
    onBack: () -> Unit,
    onValueChanged: (String) -> Unit,
    onContinue: (String) -> Unit,
    onScan: () -> Unit
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(dimensionResource(id = R.dimen.screen_standard_margin))
                .verticalScroll(rememberScrollState())
    ) {
        val address =
            remember {
                mutableStateOf(receiverAddress)
            }
        val clipboard = LocalClipboard.current
        val scope = rememberCoroutineScope()

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
        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.pageMargin)))
        BodyMedium(
            text = stringResource(id = R.string.ns_choose_who),
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            color = WalletTheme.colors.secondaryTitleText
        )
        Spacer(modifier = Modifier.height(45.dp))
        OutlinedTextField(
            value = address.value,
            onValueChange = {
                val trimAddress = it.trim()
                address.value = trimAddress
                onValueChanged(trimAddress)
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 55.dp),
            placeholder = {
                BodyMedium(
                    text = stringResource(id = R.string.ns_add_address_hint),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = WalletTheme.colors.secondaryTitleText
                )
            },
            trailingIcon = {
                if (address.value.isNotBlank()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "",
                        tint = Color.White,
                        modifier =
                            Modifier.clickable {
                                address.value = ""
                                onValueChanged("")
                            }
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_qr_scan),
                        contentDescription = "",
                        tint = Color.White,
                        modifier =
                            Modifier.clickable {
                                onScan()
                            }
                    )
                }
            },
            colors = TextFieldDefaults.customColors(),
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (address.value.isBlank()) {
            DottedBorderTextButton(
                onClick = {
                    scope.launch {
                        clipboard.pastePlainText()?.let { pastedText ->
                            address.value = pastedText
                            onValueChanged(pastedText)
                        }
                    }
                },
                text = stringResource(id = R.string.ns_paste_from_clip_board),
                modifier =
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .height(dimensionResource(id = R.dimen.button_height))
            )
        } else {
            Spacer(modifier = Modifier.height(40.dp)) // To prevent below UI come upside
        }
        Spacer(modifier = Modifier.height(40.dp))
        PrimaryButton(
            onClick = { onContinue(address.value) },
            text = stringResource(id = R.string.ns_continue).uppercase(),
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .sizeIn(
                        minWidth = dimensionResource(id = R.dimen.button_min_width),
                        minHeight = dimensionResource(id = R.dimen.button_height)
                    ),
            enabled = isContinueBtnEnabled
        )
    }
}
