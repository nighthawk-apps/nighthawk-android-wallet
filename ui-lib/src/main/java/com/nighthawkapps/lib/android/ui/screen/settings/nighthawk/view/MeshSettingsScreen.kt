package com.nighthawkapps.lib.android.ui.screen.settings.nighthawk.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.design.component.BodyMedium
import com.nighthawkapps.lib.android.ui.design.component.NighthawkTopBar
import com.nighthawkapps.lib.android.ui.design.component.NighthawkTopBarLeading
import com.nighthawkapps.lib.android.ui.design.component.PrimaryButton
import com.nighthawkapps.lib.android.ui.design.component.TitleMedium
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme
import com.nighthawkapps.lib.android.ui.screen.chat.view.MeshSettingsSection

@Composable
internal fun MeshSettingsScreen(onBack: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WalletTheme.dimens.spacingDefault)
                .padding(vertical = dimensionResource(id = R.dimen.screen_standard_margin)),
    ) {
        MeshSettingsTopBar(onBack)
        TitleMedium(text = stringResource(R.string.ns_mesh_settings_screen_title))
        Spacer(modifier = Modifier.height(8.dp))
        BodyMedium(
            text = stringResource(R.string.ns_mesh_settings_screen_body),
            color = WalletTheme.colors.secondaryTitleText,
        )
        MeshSettingsSection(showOemCopy = true)
        Spacer(modifier = Modifier.height(16.dp))
        PrimaryButton(
            onClick = onBack,
            text = stringResource(R.string.ns_tor_network_done),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ColumnScope.MeshSettingsTopBar(onBack: () -> Unit) {
    Column(modifier = Modifier.align(Alignment.Start)) {
        NighthawkTopBar(
            onLeadingClick = onBack,
            leading = NighthawkTopBarLeading.Back,
            title = stringResource(R.string.ns_mesh_settings_screen_title),
        )
        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.offset)))
    }
}
