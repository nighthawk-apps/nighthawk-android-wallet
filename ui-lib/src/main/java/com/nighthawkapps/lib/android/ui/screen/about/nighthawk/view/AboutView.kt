package com.nighthawkapps.lib.android.ui.screen.about.nighthawk.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.design.component.BodyMedium
import com.nighthawkapps.lib.android.ui.design.component.NighthawkTopBar
import com.nighthawkapps.lib.android.ui.design.component.NighthawkTopBarLeading
import com.nighthawkapps.lib.android.ui.design.component.PrimaryButton
import com.nighthawkapps.lib.android.ui.design.component.Reference
import com.nighthawkapps.lib.android.ui.design.component.TitleMedium
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme

@Composable
@Preview
fun AboutViewPreview() {
    WalletTheme(darkTheme = false) {
        Surface {
            AboutView(onBack = {}, onViewSource = {}, onCredits = {}, onTermAndCondition = {}, onViewLicence = {})
        }
    }
}

@Composable
fun AboutView(
    onBack: () -> Unit,
    onViewSource: () -> Unit,
    onCredits: () -> Unit,
    onTermAndCondition: () -> Unit,
    onViewLicence: () -> Unit
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = WalletTheme.dimens.spacingDefault)
                .padding(vertical = dimensionResource(id = R.dimen.screen_standard_margin))
                .verticalScroll(rememberScrollState())
    ) {
        NighthawkTopBar(
            onLeadingClick = onBack,
            leading = NighthawkTopBarLeading.Back,
            title = stringResource(id = R.string.ns_about),
            leadingContentDescription = stringResource(R.string.about_back_content_description),
        )
        Spacer(modifier = Modifier.height(40.dp))
        TitleMedium(
            text = stringResource(id = R.string.ns_about),
            color = colorResource(id = com.nighthawkapps.lib.android.ui.design.R.color.ns_parmaviolet)
        )
        Spacer(modifier = Modifier.height(24.dp))
        BodyMedium(text = stringResource(id = R.string.ns_about_message))
        Spacer(modifier = Modifier.height(24.dp))
        Reference(
            text = stringResource(id = R.string.ns_view_source),
            style = TextStyle(fontSize = TextUnit(12f, TextUnitType.Sp), textDecoration = TextDecoration.Underline),
            onClick = onViewSource
        )
        Spacer(modifier = Modifier.height(10.dp))
        Reference(
            text = stringResource(id = R.string.ns_credits),
            style = TextStyle(fontSize = TextUnit(12f, TextUnitType.Sp), textDecoration = TextDecoration.Underline),
            onClick = onCredits
        )
        Spacer(modifier = Modifier.height(10.dp))
        Reference(
            text = stringResource(id = R.string.ns_terms_conditions),
            style = TextStyle(fontSize = TextUnit(12f, TextUnitType.Sp), textDecoration = TextDecoration.Underline),
            onClick = onTermAndCondition
        )
        Spacer(modifier = Modifier.weight(1f))
        PrimaryButton(
            onClick = onViewLicence,
            text = stringResource(id = R.string.ns_view_licences).uppercase(),
            outerPaddingValues = PaddingValues(top = WalletTheme.dimens.spacingSmall),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}
