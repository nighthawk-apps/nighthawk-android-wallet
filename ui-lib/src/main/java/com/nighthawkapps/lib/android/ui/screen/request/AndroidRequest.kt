@file:Suppress("ktlint:standard:filename")

package com.nighthawkapps.lib.android.ui.screen.request

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.R

@Composable
internal fun MainActivity.WrapRequest(goBack: () -> Unit) {
    BackHandler(onBack = goBack)
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(id = R.string.ns_payment_request_unavailable),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
