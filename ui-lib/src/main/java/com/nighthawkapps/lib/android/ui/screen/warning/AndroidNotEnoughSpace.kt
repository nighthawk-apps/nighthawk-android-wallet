@file:Suppress("ktlint:standard:filename")

package com.nighthawkapps.lib.android.ui.screen.warning

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.screen.warning.view.NotEnoughSpaceView
import com.nighthawkapps.lib.android.ui.screen.warning.viewmodel.StorageCheckViewModel

@Composable
fun MainActivity.WrapNotEnoughSpace() {
    WrapNotEnoughSpace(this)
}

@Composable
private fun WrapNotEnoughSpace(activity: ComponentActivity) {
    val storageCheckViewModel by activity.viewModels<StorageCheckViewModel>()
    val spaceRequiredToContinue by storageCheckViewModel.spaceRequiredToContinueMegabytes.collectAsStateWithLifecycle()

    NotEnoughSpaceView(
        storageSpaceRequiredGigabytes = storageCheckViewModel.requiredStorageSpaceGigabytes,
        spaceRequiredToContinueMegabytes = spaceRequiredToContinue ?: 0
    )
}
