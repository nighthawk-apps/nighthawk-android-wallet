package com.nighthawkapps.lib.android.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.design.component.TitleLarge

/**
 * Shared Nighthawk logo + wordmark header.
 *
 * Used by the Wallet, Transfer and Settings hubs so the logo and "Nighthawk" title are
 * rendered identically (same centering and spacing) across screens. The header
 * fills the available width and centers its contents horizontally.
 *
 * [logoModifier] is applied to the logo image (e.g. wallet bandit spin).
 * [bottomSpacing] is the gap after the wordmark before following content.
 */
@Composable
fun NighthawkBrandingHeader(
    modifier: Modifier = Modifier,
    bottomSpacing: Dp = 40.dp,
    logoModifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.back_icon_size)))
        Image(
            painter = painterResource(id = R.drawable.ic_nighthawk_logo),
            contentDescription = stringResource(id = R.string.ns_logo_desc),
            contentScale = ContentScale.Inside,
            modifier = logoModifier,
        )
        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.pageMargin)))
        TitleLarge(
            text = stringResource(id = R.string.ns_nighthawk),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(bottomSpacing))
    }
}
