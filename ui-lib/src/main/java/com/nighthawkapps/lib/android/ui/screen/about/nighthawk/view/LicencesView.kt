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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.mikepenz.aboutlibraries.ui.compose.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.design.component.TitleMedium
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
fun LicencesView() {
    Box {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.screen_standard_margin)))
            TitleMedium(
                text = stringResource(id = R.string.ns_view_license),
                color = colorResource(id = com.nighthawkapps.lib.android.ui.design.R.color.ns_parmaviolet),
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            LibrariesContainer(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(dimensionResource(id = R.dimen.screen_standard_margin)),
                colors =
                    LibraryDefaults.libraryColors(
                        backgroundColor = MaterialTheme.colorScheme.surface,
                        contentColor = Color.White
                    )
            )
        }
    }
}
