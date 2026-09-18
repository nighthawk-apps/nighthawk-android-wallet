package com.nighthawkapps.lib.android.ui.screen.dex

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.NighthawkBrandingHeader
import com.nighthawkapps.lib.android.ui.design.component.BodyMedium
import com.nighthawkapps.lib.android.ui.design.component.TitleMedium
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme

@Preview
@Composable
fun DexComingSoonPreview() {
    WalletTheme(darkTheme = false) {
        Surface {
            DexComingSoonView()
        }
    }
}

@Composable
fun DexComingSoonView() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(dimensionResource(id = R.dimen.screen_standard_margin)),
    ) {
        NighthawkBrandingHeader()
        TitleMedium(text = stringResource(id = R.string.ns_dex))
        Spacer(modifier = Modifier.height(13.dp))
        BodyMedium(
            text = stringResource(id = R.string.ns_dex_coming_soon),
            color = WalletTheme.colors.secondaryTitleText,
        )
        Spacer(modifier = Modifier.height(10.dp))
        BodyMedium(text = stringResource(id = R.string.ns_dex_coming_soon_body))
    }
}
