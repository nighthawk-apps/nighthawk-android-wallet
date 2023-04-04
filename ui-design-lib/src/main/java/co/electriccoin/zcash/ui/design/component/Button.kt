package co.electriccoin.zcash.ui.design.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonDefaults.buttonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.internal.Typography

@Preview
@Composable
fun ButtonComposablePreview() {
    ZcashTheme(darkTheme = true) {
        GradientSurface {
            Column {
                PrimaryButton(onClick = { }, text = "Primary")
                SecondaryButton(onClick = { }, text = "Secondary")
                TertiaryButton(onClick = { }, text = "Tertiary")
                NavigationButton(onClick = { }, text = "Navigation")
            }
        }
    }
}

@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.then(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
        ),
        enabled = enabled,
        colors = buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            style = Typography.bodyMedium,
            text = text,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@Composable
fun SecondaryButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.then(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ),
        enabled = enabled,
        colors = buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
    ) {
        Text(
            style = MaterialTheme.typography.labelLarge,
            text = text,
            color = MaterialTheme.colorScheme.onSecondary
        )
    }
}

@Composable
fun NavigationButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.then(
            Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ),
        colors = buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
    ) {
        Text(style = MaterialTheme.typography.labelLarge, text = text, color = MaterialTheme.colorScheme.onSecondary)
    }
}

@Composable
fun TertiaryButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.then(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
        ),
        enabled = enabled,
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
        colors = buttonColors(containerColor = ZcashTheme.colors.tertiary)
    ) {
        Text(
            style = Typography.bodyMedium,
            text = text,
            color = ZcashTheme.colors.onTertiary
        )
    }
}

@Composable
fun DangerousButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.then(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ),
        colors = buttonColors(containerColor = ZcashTheme.colors.dangerous)
    ) {
        Text(
            style = MaterialTheme.typography.labelLarge,
            text = text,
            color = ZcashTheme.colors.onDangerous
        )
    }
}
