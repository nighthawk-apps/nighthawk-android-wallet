package com.nighthawkapps.lib.android.ui.screen.send.nighthawk.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiPaymentMemo
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.customColors
import com.nighthawkapps.lib.android.ui.design.component.BodyMedium
import com.nighthawkapps.lib.android.ui.design.component.NighthawkTopBar
import com.nighthawkapps.lib.android.ui.design.component.NighthawkTopBarLeading
import com.nighthawkapps.lib.android.ui.design.component.PrimaryButton
import com.nighthawkapps.lib.android.ui.design.component.TitleLarge
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme

/** Max UTF-8 bytes for payment memo (UnifOMR `u8` length / FFI `MAX_PAYMENT_MEMO_BYTES`). */
const val SEND_MEMO_MAX_CHARS = DarkfiPaymentMemo.MAX_BYTES

@Composable
fun EnterMemo(
    memoText: String,
    onBack: () -> Unit,
    onMemoChanged: (String) -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = WalletTheme.dimens.spacingDefault)
                .padding(vertical = dimensionResource(id = R.dimen.screen_standard_margin))
                .verticalScroll(rememberScrollState()),
    ) {
        NighthawkTopBar(
            onLeadingClick = onBack,
            leading = NighthawkTopBarLeading.Back,
            title = stringResource(id = R.string.ns_memo),
        )
        Spacer(modifier = Modifier.height(WalletTheme.dimens.spacingLarge))
        TitleLarge(
            text = stringResource(id = R.string.ns_add_message),
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(modifier = Modifier.height(WalletTheme.dimens.spacingDefault))
        BodyMedium(
            text = stringResource(id = R.string.send_omr_default_hint),
            color = WalletTheme.colors.secondaryTitleText,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(modifier = Modifier.height(WalletTheme.dimens.spacingLarge))
        OutlinedTextField(
            value = memoText,
            onValueChange = { next ->
                onMemoChanged(DarkfiPaymentMemo.truncateToMaxBytes(next))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                BodyMedium(
                    text = stringResource(id = R.string.ns_write_something),
                    color = WalletTheme.colors.secondaryTitleText,
                )
            },
            minLines = 4,
            colors = TextFieldDefaults.customColors(),
        )
        BodyMedium(
            text = "${DarkfiPaymentMemo.utf8Size(memoText)}/$SEND_MEMO_MAX_CHARS",
            color = WalletTheme.colors.secondaryTitleText,
            modifier = Modifier.align(Alignment.End),
        )
        Spacer(modifier = Modifier.weight(1f))
        PrimaryButton(
            onClick = onContinue,
            text = stringResource(id = R.string.ns_continue).uppercase(),
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .sizeIn(
                        minWidth = dimensionResource(id = R.dimen.button_min_width),
                        minHeight = dimensionResource(id = R.dimen.button_height),
                    ),
        )
        TextButton(
            onClick = onSkip,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            BodyMedium(
                text = stringResource(id = R.string.ns_skip).uppercase(),
                color = WalletTheme.colors.onBackgroundHeader,
            )
        }
    }
}
