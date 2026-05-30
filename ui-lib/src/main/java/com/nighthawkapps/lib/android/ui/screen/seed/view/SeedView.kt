package com.nighthawkapps.lib.android.ui.screen.seed.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.nighthawkapps.lib.android.sdk.wallet.PersistableDarkfiWallet
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.SecureScreen
import com.nighthawkapps.lib.android.ui.design.component.Body
import com.nighthawkapps.lib.android.ui.design.component.ChipGrid
import com.nighthawkapps.lib.android.ui.design.component.GradientSurface
import com.nighthawkapps.lib.android.ui.design.component.TertiaryButton
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme
import com.nighthawkapps.lib.android.ui.fixture.PersistableDarkfiWalletFixture
import kotlinx.collections.immutable.toPersistentList

@Preview("Seed")
@Composable
private fun PreviewSeed() {
    WalletTheme(darkTheme = true) {
        GradientSurface {
            Seed(
                persistableWallet = PersistableDarkfiWalletFixture.new(),
                onBack = {},
                onCopyToClipboard = {}
            )
        }
    }
}

/*
 * Note we have some things to determine regarding locking of the secrets for persistableWallet
 * (e.g. seed phrase and spending keys) which should require additional authorization to view.
 */
@Composable
fun Seed(
    persistableWallet: PersistableDarkfiWallet,
    onBack: () -> Unit,
    onCopyToClipboard: () -> Unit
) {
    SecureScreen()
    Scaffold(topBar = {
        SeedTopAppBar(onBack = onBack)
    }) { paddingValues ->
        SeedMainContent(
            persistableWallet = persistableWallet,
            onCopyToClipboard = onCopyToClipboard,
            modifier =
                Modifier.padding(
                    top = paddingValues.calculateTopPadding() + WalletTheme.dimens.spacingDefault,
                    bottom = paddingValues.calculateBottomPadding() + WalletTheme.dimens.spacingDefault,
                    start = WalletTheme.dimens.spacingDefault,
                    end = WalletTheme.dimens.spacingDefault
                )
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SeedTopAppBar(onBack: () -> Unit) {
    TopAppBar(
        title = { Text(text = stringResource(id = R.string.seed_title)) },
        navigationIcon = {
            IconButton(
                onClick = onBack
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.seed_back_content_description)
                )
            }
        }
    )
}

@Composable
private fun SeedMainContent(
    persistableWallet: PersistableDarkfiWallet,
    onCopyToClipboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        Modifier
            .fillMaxHeight()
            .verticalScroll(
                rememberScrollState()
            ).then(modifier)
    ) {
        Body(stringResource(R.string.seed_body))

        Spacer(modifier = Modifier.height(WalletTheme.dimens.spacingDefault))

        ChipGrid(persistableWallet.seedPhrase.toPersistentList())

        TertiaryButton(
            onClick = onCopyToClipboard,
            text = stringResource(R.string.seed_copy),
            outerPaddingValues =
                PaddingValues(
                    horizontal = WalletTheme.dimens.spacingNone,
                    vertical = WalletTheme.dimens.spacingSmall
                )
        )
    }
}
