package com.nighthawkapps.lib.android.ui.screen.wallet.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiAmountFormatter
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiTokenBalance
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.design.component.NighthawkHudPanel
import com.nighthawkapps.lib.android.ui.screen.wallet.model.isNativeDrk

/**
 * Extra-token list for surfaces that are not the swipe pager.
 * Native DRK is the Wallet home TOTAL page; this composable skips it and
 * does not invent a zero-balance row.
 */
@Composable
fun TokenPortfolio(
    balances: List<DarkfiTokenBalance>,
    onTokenClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = balances.filterNot { it.isNativeDrk() }
    if (rows.isEmpty()) return
    NighthawkHudPanel(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.ns_wallet_tokens_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        rows.forEach { token ->
            val amount = DarkfiAmountFormatter.formatAtomic(token.balanceAtomic)
            Text(
                text = "${token.displayName}  $amount",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onTokenClick(token.tokenId) }
                        .padding(vertical = 8.dp),
            )
        }
    }
}
