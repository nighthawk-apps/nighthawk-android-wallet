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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.design.component.BodyMedium
import com.nighthawkapps.lib.android.ui.design.component.NighthawkTopBar
import com.nighthawkapps.lib.android.ui.design.component.NighthawkTopBarLeading
import com.nighthawkapps.lib.android.ui.design.component.PrimaryButton
import com.nighthawkapps.lib.android.ui.design.component.TitleLarge
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme

@Composable
fun ReviewSend(
    amountLabel: String,
    recipient: String,
    memo: String?,
    feeLabel: String?,
    isEstimatingFee: Boolean,
    isSendEnabled: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onSend: () -> Unit,
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
            title = stringResource(id = R.string.ns_review_and_send),
        )
        Spacer(modifier = Modifier.height(WalletTheme.dimens.spacingLarge))
        TitleLarge(
            text = stringResource(id = R.string.ns_review_and_send),
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(modifier = Modifier.height(WalletTheme.dimens.spacingLarge))

        ReviewRow(label = stringResource(R.string.send_amount_label), value = amountLabel)
        ReviewRow(label = stringResource(R.string.send_recipient_label), value = recipient)
        ReviewRow(
            label = stringResource(R.string.ns_network_fee),
            value = feeLabel ?: stringResource(R.string.send_fee_unknown),
        )
        ReviewRow(
            label = stringResource(R.string.ns_sync_method),
            value = stringResource(R.string.send_omr_scheme_label),
        )
        memo?.takeIf { it.isNotBlank() }?.let {
            ReviewRow(label = stringResource(R.string.ns_memo), value = it)
        }

        if (isEstimatingFee) {
            Spacer(modifier = Modifier.height(WalletTheme.dimens.spacingDefault))
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        errorMessage?.let { msg ->
            Spacer(modifier = Modifier.height(WalletTheme.dimens.spacingSmall))
            BodyMedium(text = msg, color = WalletTheme.colors.secondaryTitleText)
        }

        Spacer(modifier = Modifier.height(WalletTheme.dimens.spacingDefault))
        BodyMedium(
            text = stringResource(id = R.string.send_omr_default_hint),
            color = WalletTheme.colors.secondaryTitleText,
        )

        Spacer(modifier = Modifier.weight(1f))
        PrimaryButton(
            onClick = onSend,
            text = stringResource(id = R.string.ns_send_drk).uppercase(),
            enabled = isSendEnabled,
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .sizeIn(
                        minWidth = dimensionResource(id = R.dimen.button_min_width),
                        minHeight = dimensionResource(id = R.dimen.button_height),
                    ),
        )
    }
}

@Composable
private fun ReviewRow(
    label: String,
    value: String,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = WalletTheme.dimens.spacingSmall)) {
        BodyMedium(text = label, color = WalletTheme.colors.secondaryTitleText)
        Spacer(modifier = Modifier.height(WalletTheme.dimens.spacingXtiny))
        BodyMedium(text = value)
    }
}
