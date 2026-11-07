package com.nighthawkapps.lib.android.ui.screen.about.nighthawk.view

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.ui.compose.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.design.component.NighthawkTopBar
import com.nighthawkapps.lib.android.ui.design.component.NighthawkTopBarLeading
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme

@Preview
@Composable
fun LicenceViewPreview() {
    WalletTheme(darkTheme = false) {
        Surface {
            LicencesView()
        }
    }
}

@Composable
fun LicencesView(onBack: () -> Unit = {}) {
    Box {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = WalletTheme.dimens.spacingDefault)
                    .padding(vertical = dimensionResource(id = R.dimen.screen_standard_margin)),
        ) {
            NighthawkTopBar(
                onLeadingClick = onBack,
                leading = NighthawkTopBarLeading.Back,
                title = stringResource(id = R.string.ns_view_license),
                leadingContentDescription = stringResource(R.string.about_back_content_description),
            )
            Spacer(modifier = Modifier.height(16.dp))
            LibrariesContainer(
                modifier = Modifier.fillMaxSize(),
                colors =
                    LibraryDefaults.libraryColors(
                        backgroundColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )
            )
        }
    }
}
