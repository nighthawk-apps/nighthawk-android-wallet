package com.nighthawkapps.lib.android.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.design.component.TitleLarge

/**
 * Shared Nighthawk wordmark header (no logo mark on sub-screens).
 *
 * Home tabs and the connecting splash leave this off so the wallpaper wordmark
 * is the only title. The header fills the available width and centers its
 * contents horizontally.
 *
 * [logoModifier] is retained for call-site compatibility; unused after the logo
 * mark was removed from sub-screens.
 * [bottomSpacing] is the gap after the wordmark before following content.
 */
@Composable
fun NighthawkBrandingHeader(
    modifier: Modifier = Modifier,
    bottomSpacing: Dp = 40.dp,
    @Suppress("UNUSED_PARAMETER") logoModifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.back_icon_size)))
        TitleLarge(
            text = stringResource(id = R.string.ns_nighthawk),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(bottomSpacing))
    }
}
