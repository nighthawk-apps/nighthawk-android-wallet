package com.nighthawkapps.lib.android.ui.screen.settings.nighthawk

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.screen.settings.nighthawk.view.TorNetworkSettingsScreen

@Composable
internal fun MainActivity.AndroidTorNetworkSettings(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    TorNetworkSettingsScreen(onBack = onBack)
}
