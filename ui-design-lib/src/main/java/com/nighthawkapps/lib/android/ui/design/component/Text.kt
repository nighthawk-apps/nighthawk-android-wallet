package com.nighthawkapps.lib.android.ui.design.component

import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import com.nighthawkapps.lib.android.ui.design.R
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme
import com.nighthawkapps.lib.android.ui.design.theme.internal.Typography

@Composable
fun Header(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    color: Color = WalletTheme.colors.onBackgroundHeader,
) {
    Text(
        text = text,
        color = color,
        textAlign = textAlign,
        style = Typography.headlineLarge,
        modifier = modifier
    )
}

@Composable
fun Body(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign = TextAlign.Start,
    color: Color = MaterialTheme.colorScheme.onBackground
) {
    Text(
        text = text,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
        style = Typography.bodyLarge,
        color = color,
        modifier = modifier
    )
}

@Composable
fun BodyMedium(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    color: Color = MaterialTheme.colorScheme.onBackground,
    textDecoration: TextDecoration? = null
) {
    Text(
        text = text,
        style = Typography.bodyMedium,
        color = color,
        modifier = modifier,
        textAlign = textAlign,
        textDecoration = textDecoration
    )
}

@Composable
fun BodyMedium(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
) {
    Text(
        text = text,
        style = Typography.bodyMedium,
        modifier = modifier,
        textAlign = textAlign
    )
}

@Composable
fun BodySmall(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    color: Color = MaterialTheme.colorScheme.onBackground,
) {
    Text(
        text = text,
        style = Typography.bodySmall,
        color = color,
        modifier = modifier,
        textAlign = textAlign
    )
}

@Composable
fun BalanceText(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    color: Color = MaterialTheme.colorScheme.onBackground,
) {
    Text(
        text = text,
        style = Typography.headlineMedium,
        color = color,
        modifier = modifier,
        textAlign = textAlign
    )
}

@Composable
fun TitleMedium(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    color: Color = WalletTheme.colors.onBackgroundHeader,
) {
    Text(
        text = text,
        style = Typography.titleMedium,
        color = color,
        modifier = modifier,
        textAlign = textAlign
    )
}

@Composable
fun TitleLarge(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    color: Color = WalletTheme.colors.onBackgroundHeader,
) {
    Text(
        text = text,
        style = Typography.titleLarge,
        color = color,
        modifier = modifier,
        textAlign = textAlign
    )
}

@Composable
fun ListItem(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = WalletTheme.typography.listItem,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
    )
}

@Composable
fun ListHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = WalletTheme.typography.listItem,
        color = WalletTheme.colors.onBackgroundHeader,
        modifier = modifier
    )
}

@Composable
fun Reference(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    onClick: () -> Unit
) {
    Text(
        text = text,
        style =
            style
                .merge(TextStyle(color = WalletTheme.colors.reference)),
        modifier =
            modifier
                .minimumInteractiveComponentSize()
                .clickable(onClick = onClick),
    )
}

/**
 * Display a formatted DRK amount header using the wallet balance typeface (glyph/font substitutes the dollar slot).
 *
 * @param amount formatted DRK amount text to show
 * @param modifier to modify the Text UI element as needed
 */
@Composable
fun HeaderWithDrkIcon(
    amount: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = stringResource(R.string.amount_with_drk_currency_symbol, amount),
        style = WalletTheme.typography.drkBalance,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
    )
}

@Composable
fun BodyWithFiatCurrencySymbol(
    amount: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = amount,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
    )
}
