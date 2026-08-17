package com.nighthawkapps.lib.android.ui.screen.send.nighthawk.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiAmountFormatter
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiTokenBalance
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.customColors
import com.nighthawkapps.lib.android.ui.design.component.BodyMedium
import com.nighthawkapps.lib.android.ui.design.component.NighthawkTopBar
import com.nighthawkapps.lib.android.ui.design.component.NighthawkTopBarLeading
import com.nighthawkapps.lib.android.ui.design.component.PrimaryButton
import com.nighthawkapps.lib.android.ui.design.component.TitleLarge
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterAmount(
    amountText: String,
    spendableBalanceLabel: String,
    tokenBalances: List<DarkfiTokenBalance>,
    selectedTokenId: String?,
    isContinueEnabled: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onAmountChanged: (String) -> Unit,
    onTokenSelected: (String?) -> Unit,
    onMaxAmount: (() -> Unit)? = null,
    onContinue: () -> Unit,
    onScanPaymentRequest: () -> Unit,
) {
    var tokenMenuExpanded by remember { mutableStateOf(false) }

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
            title = stringResource(id = R.string.ns_send_money),
        )
        Image(
            painter = painterResource(id = R.drawable.ic_nighthawk_logo),
            contentDescription = stringResource(id = R.string.ns_logo_desc),
            contentScale = ContentScale.Inside,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.pageMargin)))
        TitleLarge(
            text = stringResource(id = R.string.ns_choose_send),
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(modifier = Modifier.height(WalletTheme.dimens.spacingSmall))
        BodyMedium(
            text = spendableBalanceLabel,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            color = WalletTheme.colors.secondaryTitleText,
        )
        Spacer(modifier = Modifier.height(WalletTheme.dimens.spacingLarge))

        if (tokenBalances.isNotEmpty()) {
            val selectedLabel =
                tokenBalances.firstOrNull { it.tokenId == selectedTokenId }?.displayName
                    ?: selectedTokenId.orEmpty().ifEmpty { "DRK" }
            ExposedDropdownMenuBox(
                expanded = tokenMenuExpanded,
                onExpandedChange = { tokenMenuExpanded = it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = selectedLabel,
                    onValueChange = {},
                    readOnly = true,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                    label = { Text(stringResource(R.string.send_token_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tokenMenuExpanded) },
                    colors = TextFieldDefaults.customColors(),
                )
                ExposedDropdownMenu(
                    expanded = tokenMenuExpanded,
                    onDismissRequest = { tokenMenuExpanded = false },
                ) {
                    tokenBalances.forEach { token ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "${token.displayName} (${DarkfiAmountFormatter.formatAtomic(token.balanceAtomic)})",
                                )
                            },
                            onClick = {
                                onTokenSelected(token.tokenId)
                                tokenMenuExpanded = false
                            },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(WalletTheme.dimens.spacingDefault))
        }

        OutlinedTextField(
            value = amountText,
            onValueChange = onAmountChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.send_amount_label)) },
            trailingIcon =
                onMaxAmount?.let { onMax ->
                    {
                        TextButton(onClick = onMax) {
                            Text(
                                text = "MAX",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
            singleLine = true,
            colors = TextFieldDefaults.customColors(),
        )

        errorMessage?.let { msg ->
            Spacer(modifier = Modifier.height(WalletTheme.dimens.spacingSmall))
            BodyMedium(text = msg, color = WalletTheme.colors.secondaryTitleText)
        }

        Spacer(modifier = Modifier.height(WalletTheme.dimens.spacingLarge))
        BodyMedium(
            text = stringResource(id = R.string.ns_scan_payment_request_text),
            color = WalletTheme.colors.secondaryTitleText,
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable(onClick = onScanPaymentRequest),
        )

        Spacer(modifier = Modifier.weight(1f))
        PrimaryButton(
            onClick = onContinue,
            text = stringResource(id = R.string.ns_continue).uppercase(),
            enabled = isContinueEnabled,
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .sizeIn(
                        minWidth = dimensionResource(id = R.dimen.button_min_width),
                        minHeight = dimensionResource(id = R.dimen.button_height),
                    ),
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}
