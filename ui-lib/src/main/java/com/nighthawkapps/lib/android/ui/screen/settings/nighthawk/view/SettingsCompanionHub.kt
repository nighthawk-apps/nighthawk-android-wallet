package com.nighthawkapps.lib.android.ui.screen.settings.nighthawk.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.VersionInfo
import com.nighthawkapps.lib.android.ui.design.component.BodyMedium
import com.nighthawkapps.lib.android.ui.design.component.TitleMedium
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme

/** Right fold pane when no settings detail is selected. */
@Composable
internal fun SettingsCompanionHub(versionInfo: VersionInfo) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(WalletTheme.dimens.spacingDefault),
    ) {
        TitleMedium(
            text = stringResource(id = R.string.ns_settings).uppercase(),
            color = WalletTheme.colors.secondaryTitleText,
        )
        Spacer(modifier = Modifier.height(8.dp))
        BodyMedium(
            text = stringResource(id = R.string.ns_settings_fold_hint),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(24.dp))
        TitleMedium(
            text = stringResource(id = R.string.ns_about),
            color = WalletTheme.colors.secondaryTitleText,
        )
        Spacer(modifier = Modifier.height(8.dp))
        BodyMedium(
            text = stringResource(id = R.string.ns_about_text, versionInfo.versionName),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
