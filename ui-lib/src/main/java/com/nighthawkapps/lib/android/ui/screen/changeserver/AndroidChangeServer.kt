package com.nighthawkapps.lib.android.ui.screen.changeserver

import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.screen.changeserver.view.ChangeServer
import com.nighthawkapps.lib.android.ui.screen.changeserver.viewmodel.ChangeServerViewModel
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.WalletViewModel
import kotlinx.coroutines.launch

@Composable
internal fun MainActivity.AndroidChangeServer(onBack: () -> Unit) {
    val walletViewModel by viewModels<WalletViewModel>()
    AndroidChangeServer(walletViewModel = walletViewModel, onBack = onBack)
}

@Composable
private fun AndroidChangeServer(
    walletViewModel: WalletViewModel,
    onBack: () -> Unit,
) {
    val changeServerViewModel = viewModel<ChangeServerViewModel>()
    val selectedPreset by changeServerViewModel.selectedPreset.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    ChangeServer(
        onBack = onBack,
        presetOptionList = changeServerViewModel.presetOptions(),
        selectedPreset = selectedPreset,
        onPresetSelected = { preset ->
            if (preset == null) return@ChangeServer
            scope.launch {
                changeServerViewModel.updateSelectedPreset(preset)
                walletViewModel.updateDarkfiEndpoint(preset.endpoint)
            }
        },
    )
}
