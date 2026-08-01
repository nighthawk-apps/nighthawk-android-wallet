@file:Suppress("ktlint:standard:filename")

package com.nighthawkapps.lib.android.ui.design.component

import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nighthawkapps.lib.android.spackle.model.Progress
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme

// Eventually rename to GradientLinearProgressIndicator
@Composable
fun PinkProgress(
    progress: Progress,
    modifier: Modifier = Modifier
) {
    // Needs custom implementation to apply gradient
    LinearProgressIndicator(
        progress = { progress.percent() },
        modifier = modifier,
        color = WalletTheme.colors.progressStart,
        trackColor = WalletTheme.colors.progressBackground,
    )
}

private fun Progress.percent() = (current.value + 1.toFloat()) / (last.value + 1).toFloat()
