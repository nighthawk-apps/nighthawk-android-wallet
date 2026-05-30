package com.nighthawkapps.lib.android.ui.design.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme
import androidx.compose.material3.HorizontalDivider as MaterialHorizontalDivider

@Preview
@Composable
fun PreviewMaxWidthDivider() {
    WalletTheme(darkTheme = true) {
        Surface {
            MaxWidthHorizontalDivider()
        }
    }
}

@Composable
fun MaxWidthHorizontalDivider(
    thickness: Dp = 1.dp,
    color: Color = WalletTheme.colors.divider
) {
    MaterialHorizontalDivider(modifier = Modifier.fillMaxWidth(), thickness = thickness, color = color)
}

@Composable
fun HorizontalDivider(
    modifier: Modifier,
    thickness: Dp = 1.dp,
    color: Color = WalletTheme.colors.divider
) {
    MaterialHorizontalDivider(modifier = modifier, thickness = thickness, color = color)
}
