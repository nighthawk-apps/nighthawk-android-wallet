package com.nighthawkapps.lib.android.ui.design.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.ui.design.R
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme

enum class NighthawkTopBarLeading {
    Back,
    Close,
    None,
}

/**
 * Shared top chrome for stacked Nighthawk screens (Phase 1–2 audit).
 * 44dp minimum hit target; optional title aligned with iOS NighthawkTopBar.
 */
@Composable
fun NighthawkTopBar(
    onLeadingClick: (() -> Unit)? = null,
    leading: NighthawkTopBarLeading = NighthawkTopBarLeading.Back,
    title: String? = null,
    modifier: Modifier = Modifier,
    leadingContentDescription: String? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .padding(horizontal = WalletTheme.dimens.spacingTiny),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (leading) {
            NighthawkTopBarLeading.None -> {
                Spacer(modifier = Modifier.size(44.dp))
            }

            else -> {
                val icon: ImageVector =
                    if (leading == NighthawkTopBarLeading.Close) {
                        Icons.Filled.Close
                    } else {
                        Icons.AutoMirrored.Filled.ArrowBack
                    }
                val desc =
                    leadingContentDescription
                        ?: if (leading == NighthawkTopBarLeading.Close) {
                            stringResource(id = R.string.nighthawk_topbar_close)
                        } else {
                            stringResource(id = R.string.nighthawk_topbar_back)
                        }
                IconButton(
                    onClick = { onLeadingClick?.invoke() },
                    modifier = Modifier.size(44.dp),
                    enabled = onLeadingClick != null,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = desc,
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        if (title != null) {
            Spacer(modifier = Modifier.width(WalletTheme.dimens.spacingTiny))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = WalletTheme.colors.secondaryTitleText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
