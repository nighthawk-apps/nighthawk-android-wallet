package com.nighthawkapps.lib.android.ui.screen.onboarding.nighthawk.view

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiChainBounds
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.design.component.BodyMedium
import com.nighthawkapps.lib.android.ui.design.component.NighthawkTopBar
import com.nighthawkapps.lib.android.ui.design.component.NighthawkTopBarLeading
import com.nighthawkapps.lib.android.ui.design.component.PrimaryButton
import com.nighthawkapps.lib.android.ui.design.component.TitleLarge
import com.nighthawkapps.lib.android.ui.design.component.TitleMedium
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.WalletViewModel
import com.nighthawkapps.lib.android.ui.screen.onboarding.viewmodel.OnboardingViewModel
import com.nighthawkapps.lib.android.ui.screen.restore.model.SeedPhraseValidation
import com.nighthawkapps.lib.android.ui.screen.restore.viewmodel.RestoreViewModel
import com.nighthawkapps.lib.android.ui.screen.wallet.persistImportedWallet

private val TOKEN_SPLIT_REGEX = "\\s+".toRegex()

@Preview
@Composable
fun RestorePreview() {
    WalletTheme(darkTheme = false) {
        Surface {
            Restore(isSeedValid = false, onSeedValueChanged = {}, onContinue = { _, _ -> }) {}
        }
    }
}

@Composable
internal fun RestoreWallet(activity: ComponentActivity) {
    val walletViewModel by activity.viewModels<WalletViewModel>()
    val onBoardingViewModel by activity.viewModels<OnboardingViewModel>()
    val restoreViewModel by activity.viewModels<RestoreViewModel>()
    val phraseValid by restoreViewModel.seedPhraseValidation.collectAsStateWithLifecycle(initialValue = null)
    val isSeedValid = phraseValid is SeedPhraseValidation.Valid

    val onSeedValueChanged = { seedPhrase: String ->
        restoreViewModel.userWordList.set(
            seedPhrase
                .trim()
                .split(TOKEN_SPLIT_REGEX)
                .filter { it.isNotBlank() }
                .map { it.lowercase() },
        )
    }
    val onContinue = { _: String, birthdayHeight: Long? ->
        walletViewModel.persistImportedWallet(
            restoreViewModel.userWordList.current.value,
            birthdayHeight,
        )
    }
    Restore(
        isSeedValid = isSeedValid,
        onSeedValueChanged = onSeedValueChanged,
        onContinue = onContinue,
    ) {
        onBoardingViewModel.setIsImporting(false)
    }
}

@Composable
internal fun Restore(
    isSeedValid: Boolean,
    onSeedValueChanged: (seed: String) -> Unit,
    onContinue: (seedPhrase: String, birthdayHeight: Long?) -> Unit,
    onBack: () -> Unit,
) {
    val scrollState = rememberScrollState()
    var seeds by remember { mutableStateOf("") }
    var birthday by remember { mutableStateOf("") }
    val birthdayDigits = birthday.filter { it.isDigit() }
    val birthdayValue = birthdayDigits.toLongOrNull()
    val birthdayValid =
        birthdayDigits.isEmpty() ||
            (birthdayValue != null && birthdayValue >= DarkfiChainBounds.MIN_WALLET_BIRTHDAY_HEIGHT)

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = WalletTheme.dimens.spacingDefault)
                .padding(vertical = dimensionResource(id = R.dimen.screen_standard_margin))
                .verticalScroll(scrollState),
    ) {
        NighthawkTopBar(
            onLeadingClick = onBack,
            leading = NighthawkTopBarLeading.Back,
            title = null,
            leadingContentDescription = stringResource(R.string.restore_back_content_description),
        )
        Image(
            painter = painterResource(id = R.drawable.ic_nighthawk_logo),
            contentDescription = stringResource(id = R.string.ns_logo_desc),
            contentScale = ContentScale.Inside,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.top_margin_back_btn)))
        TitleLarge(
            text = stringResource(id = R.string.ns_nighthawk),
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.offset)))
        TitleMedium(
            text = stringResource(id = R.string.ns_restore_from_backup),
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.text_margin)))
        BodyMedium(
            text = stringResource(id = R.string.ns_restore_wallet_text),
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .fillMaxWidth(0.7f)
                    .align(Alignment.CenterHorizontally),
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = seeds,
            onValueChange = {
                seeds = it
                onSeedValueChanged(seeds)
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 109.dp),
            label = {
                BodyMedium(text = stringResource(id = R.string.ns_your_seed_phrase))
            },
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (isSeedValid) MaterialTheme.colorScheme.primary else Color.White,
                    unfocusedBorderColor = if (isSeedValid) MaterialTheme.colorScheme.primary else Color.White,
                ),
        )
        Spacer(modifier = Modifier.size(21.dp))
        BodyMedium(
            text = stringResource(id = R.string.restore_birthday_body),
            color = WalletTheme.colors.secondaryTitleText,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = birthdayDigits,
            onValueChange = { birthday = it.filter { ch -> ch.isDigit() } },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            label = {
                BodyMedium(text = stringResource(id = R.string.ns_birthday_height))
            },
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.White,
                ),
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
            singleLine = true,
            supportingText = {
                BodyMedium(
                    text = stringResource(id = R.string.restore_birthday_hint),
                    color = WalletTheme.colors.secondaryTitleText,
                )
            },
        )
        Spacer(modifier = Modifier.height(29.dp))
        PrimaryButton(
            onClick = {
                onContinue(
                    seeds,
                    birthdayDigits.toLongOrNull()?.takeIf {
                        it >= DarkfiChainBounds.MIN_WALLET_BIRTHDAY_HEIGHT
                    },
                )
            },
            text = stringResource(id = R.string.ns_continue).uppercase(),
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .sizeIn(
                        minWidth = dimensionResource(id = R.dimen.button_min_width),
                        minHeight = dimensionResource(id = R.dimen.button_height),
                    ),
            enabled = isSeedValid && birthdayValid,
        )
        TextButton(
            onClick = { onContinue(seeds, null) },
            enabled = isSeedValid,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            BodyMedium(text = stringResource(id = R.string.restore_birthday_button_skip))
        }
    }
}
