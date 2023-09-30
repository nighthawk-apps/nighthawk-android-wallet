package co.electriccoin.zcash.ui.screen.changeserver

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.electriccoin.zcash.ui.MainActivity
import co.electriccoin.zcash.ui.screen.changeserver.view.ChangeServer
import co.electriccoin.zcash.ui.screen.changeserver.viewmodel.ChangeServerViewModel
import co.electriccoin.zcash.ui.screen.home.viewmodel.WalletViewModel

@Composable
internal fun MainActivity.AndroidChangeServer(onBack: () -> Unit) {
    AndroidChangeServer(activity = this, onBack = onBack)
}

@Composable
internal fun AndroidChangeServer(activity: ComponentActivity, onBack: () -> Unit) {

    val walletViewModel by activity.viewModels<WalletViewModel>()
    val changeServerViewModel = viewModel<ChangeServerViewModel>()
    val selectedServer by changeServerViewModel.selectedServer.collectAsStateWithLifecycle()

    ChangeServer(
        onBack = onBack,
        serverOptionList = changeServerViewModel.getServerOptionList(),
        selectedServer = selectedServer,
        onServerSelected = {
            changeServerViewModel.updateSelectedServer(it)
            walletViewModel.updateLightWalletEndPoint(it)
            onBack()
        }
    )
}