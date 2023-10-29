package co.electriccoin.zcash.ui.design.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Divider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.theme.ZcashTheme

@Preview
@Composable
fun PreviewMaxWidthDivider() {
    ZcashTheme(darkTheme = true) {
        Surface {
            MaxWidthHorizontalDivider()
        }
    }
}

@Composable
fun MaxWidthHorizontalDivider(
    thickness: Dp = 1.dp,
    color: Color = ZcashTheme.colors.divider
) {
    HorizontalDivider(modifier = Modifier.fillMaxWidth(), thickness = thickness, color = color)
}

@Composable
fun HorizontalDivider(
    modifier: Modifier,
    thickness: Dp = 1.dp,
    color: Color = ZcashTheme.colors.divider
) {
    Divider(modifier = modifier, thickness = thickness, color = color)
}
