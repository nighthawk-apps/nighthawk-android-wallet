package com.nighthawkapps.lib.android.ui.design.component

import androidx.compose.foundation.background
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme

@Composable
fun GradientSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        color = Color.Transparent,
        modifier =
            modifier
                .background(WalletTheme.colors.surfaceGradient()),
        shape = RectangleShape,
        content = content
    )
}
