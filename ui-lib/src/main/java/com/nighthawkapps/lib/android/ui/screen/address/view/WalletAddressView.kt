@file:Suppress("TooManyFunctions", "UnusedPrivateMember")

package com.nighthawkapps.lib.android.ui.screen.address.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDownCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiWalletAddresses
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.design.MINIMAL_WEIGHT
import com.nighthawkapps.lib.android.ui.design.component.Body
import com.nighthawkapps.lib.android.ui.design.component.GradientSurface
import com.nighthawkapps.lib.android.ui.design.component.ListHeader
import com.nighthawkapps.lib.android.ui.design.component.ListItem
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme
import com.nighthawkapps.lib.android.ui.screen.address.WalletAddressesTag

@Preview("WalletAddresses")
@Composable
private fun ComposablePreview() {
    WalletTheme(darkTheme = true) {
        GradientSurface {
            WalletAddresses(
                DarkfiWalletAddresses(
                    privateAddresses = listOf("darkfi_preview_private"),
                ),
                onBack = {},
                onCopyToClipboard = {}
            )
        }
    }
}

@Composable
fun WalletAddresses(
    walletAddresses: DarkfiWalletAddresses,
    onBack: () -> Unit,
    onCopyToClipboard: (String) -> Unit
) {
    Column {
        WalletDetailTopAppBar(onBack)
        WalletDetailAddresses(
            walletAddresses = walletAddresses,
            onCopyToClipboard = onCopyToClipboard,
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun WalletDetailTopAppBar(onBack: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(id = R.string.wallet_address_title)
            )
        },
        navigationIcon = {
            IconButton(
                onClick = onBack
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.wallet_address_back_content_description)
                )
            }
        }
    )
}

private val BIG_INDICATOR_WIDTH = 24.dp

@Composable
private fun WalletDetailAddresses(
    walletAddresses: DarkfiWalletAddresses,
    onCopyToClipboard: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Image(
                painter = ColorPainter(WalletTheme.colors.highlight),
                contentDescription = "",
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .width(BIG_INDICATOR_WIDTH)
            )

            Column(Modifier.fillMaxWidth()) {
                val defaultAddress = walletAddresses.privateAddresses.firstOrNull().orEmpty()
                if (defaultAddress.isNotEmpty()) {
                    ExpandableRow(
                        title = stringResource(R.string.ns_private_address),
                        content = defaultAddress,
                        isInitiallyExpanded = true,
                        onCopyToClipboard = onCopyToClipboard
                    )
                }
            }
        }
    }
}

// Removed unused ConfidentialReceiveRow and PublicReceiveRow

@Composable
private fun ExpandableRow(
    title: String,
    content: String,
    isInitiallyExpanded: Boolean,
    onCopyToClipboard: (String) -> Unit
) {
    var expandedState by rememberSaveable { mutableStateOf(isInitiallyExpanded) }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable { expandedState = !expandedState }
                    .padding(
                        horizontal = WalletTheme.dimens.spacingDefault,
                        vertical = WalletTheme.dimens.spacingTiny
                    )
        ) {
            ListItem(text = title)
            Spacer(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(MINIMAL_WEIGHT)
            )
            ExpandableArrow(expandedState)
        }
        if (expandedState) {
            Body(
                content,
                modifier =
                    Modifier
                        .clickable { onCopyToClipboard(content) }
                        .padding(
                            horizontal = WalletTheme.dimens.spacingDefault,
                            vertical = WalletTheme.dimens.spacingTiny
                        ).testTag(WalletAddressesTag.WALLET_ADDRESS)
            )
        }
    }
}

private const val NINETY_DEGREES = 90f

@Composable
private fun ExpandableArrow(isExpanded: Boolean) {
    Icon(
        imageVector = Icons.Filled.ArrowDropDownCircle,
        contentDescription =
            if (isExpanded) {
                stringResource(id = R.string.wallet_address_hide)
            } else {
                stringResource(id = R.string.wallet_address_show)
            },
        modifier =
            if (isExpanded) {
                Modifier
            } else {
                Modifier.rotate(NINETY_DEGREES)
            },
        tint = MaterialTheme.colorScheme.onBackground
    )
}
