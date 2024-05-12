package co.electriccoin.zcash.ui.screen.changeserver

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.z.ecc.android.sdk.type.ServerValidation
import cash.z.ecc.sdk.extension.isValid
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.ui.MainActivity
import co.electriccoin.zcash.ui.common.showMessage
import co.electriccoin.zcash.ui.screen.changeserver.model.validateCustomServerValue
import co.electriccoin.zcash.ui.screen.changeserver.view.ChangeServer
import co.electriccoin.zcash.ui.screen.changeserver.viewmodel.ChangeServerViewModel
import co.electriccoin.zcash.ui.screen.home.viewmodel.WalletViewModel
import kotlinx.coroutines.launch

@Composable
internal fun MainActivity.AndroidChangeServer(onBack: () -> Unit) {
    AndroidChangeServer(activity = this, onBack = onBack)
}

@Composable
internal fun AndroidChangeServer(activity: ComponentActivity, onBack: () -> Unit) {

    val walletViewModel by activity.viewModels<WalletViewModel>()
    val changeServerViewModel = viewModel<ChangeServerViewModel>()
    val selectedServer by changeServerViewModel.selectedServer.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    ChangeServer(
        onBack = onBack,
        serverOptionList = changeServerViewModel.getServerOptionList(),
        selectedServer = selectedServer,
        onServerSelected = { newServer ->
            if (newServer == null) return@ChangeServer
            scope.launch {
                walletViewModel.synchronizer.value?.let {
                    val lightWalletEndpoint = LightWalletEndpoint(newServer.host, newServer.port, newServer.isSecure)
                    if (lightWalletEndpoint.isValid().not() || validateCustomServerValue("${newServer.host}:${newServer.port}" ).not()) {
                        activity.showMessage("Invalid server details")
                        return@launch
                    }
                    val serverValidation = it.validateServerEndpoint(
                        activity,
                        lightWalletEndpoint
                    )
                    when (serverValidation) {
                        is ServerValidation.InValid -> activity.showMessage("Not able to change server ${serverValidation.reason}")
                        ServerValidation.Running -> {}
                        ServerValidation.Valid -> {
                            changeServerViewModel.updateSelectedServer(newServer)
                            walletViewModel.updateLightWalletEndPoint(newServer)
                            onBack()
                        }
                    }
                }
            }
        }
    )
}