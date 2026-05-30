package com.nighthawkapps.lib.android.ui.screen.warning.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.design.component.Body
import com.nighthawkapps.lib.android.ui.design.component.BodyMedium
import com.nighthawkapps.lib.android.ui.design.component.GradientSurface
import com.nighthawkapps.lib.android.ui.design.component.Header
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme

@Preview("NotEnoughSpace")
@Composable
private fun NotEnoughSpacePreview() {
    WalletTheme {
        GradientSurface {
            NotEnoughSpaceView(
                storageSpaceRequiredGigabytes = 1,
                spaceRequiredToContinueMegabytes = 300
            )
        }
    }
}

@Composable
fun NotEnoughSpaceView(
    storageSpaceRequiredGigabytes: Int,
    spaceRequiredToContinueMegabytes: Int
) {
    Column(
        Modifier
            .background(MaterialTheme.colorScheme.background)
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(painterResource(id = R.drawable.not_enough_space), "", Modifier.fillMaxWidth())
        Spacer(Modifier.height(32.dp))
        Header(text = stringResource(id = R.string.not_enough_space_title))
        Spacer(Modifier.height(32.dp))
        Body(
            text = stringResource(id = R.string.not_enough_space_description, storageSpaceRequiredGigabytes),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(64.dp))
        BodyMedium(
            text = stringResource(id = R.string.space_required_to_continue, spaceRequiredToContinueMegabytes),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
