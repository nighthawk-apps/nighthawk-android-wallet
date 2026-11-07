package com.nighthawkapps.lib.android.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Retained as a no-op for call-site compatibility after removing in-app
 * Nighthawk logo and wordmark headers.
 */
@Composable
fun NighthawkBrandingHeader(
    @Suppress("UNUSED_PARAMETER") modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") bottomSpacing: Dp = 0.dp,
    @Suppress("UNUSED_PARAMETER") logoModifier: Modifier = Modifier,
) = Unit
