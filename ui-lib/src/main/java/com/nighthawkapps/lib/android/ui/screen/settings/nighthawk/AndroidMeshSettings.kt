package com.nighthawkapps.lib.android.ui.screen.settings.nighthawk

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.screen.settings.nighthawk.view.MeshSettingsScreen

@Composable
internal fun MainActivity.AndroidMeshSettings(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    MeshSettingsScreen(onBack = onBack)
}
