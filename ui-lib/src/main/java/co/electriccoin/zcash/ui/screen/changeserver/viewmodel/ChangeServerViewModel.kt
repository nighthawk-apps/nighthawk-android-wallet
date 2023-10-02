package co.electriccoin.zcash.ui.screen.changeserver.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.model.ZcashNetwork
import cash.z.ecc.sdk.type.fromResources
import co.electriccoin.zcash.ui.common.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.preference.EncryptedPreferenceKeys
import co.electriccoin.zcash.ui.preference.EncryptedPreferenceSingleton
import co.electriccoin.zcash.ui.screen.changeserver.model.DEFAULT_REGION
import co.electriccoin.zcash.ui.screen.changeserver.model.LightWalletServer
import co.electriccoin.zcash.ui.screen.changeserver.model.MainnetServer
import co.electriccoin.zcash.ui.screen.changeserver.model.TestnetServer
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChangeServerViewModel(private val application: Application) : AndroidViewModel(application = application) {

    val selectedServer = flow {
        val pref = EncryptedPreferenceSingleton.getInstance(application)
        val isMainNet = ZcashNetwork.fromResources(application).isMainnet()
        emit(
            EncryptedPreferenceKeys.SELECTED_SERVER.getValue(pref).let {
                if (isMainNet) {
                    MainnetServer.getLightWalletServer(it)
                } else {
                    TestnetServer.getLightWalletServer(it)
                }
            }
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
        null
    )


    fun getServerOptionList(): ImmutableList<LightWalletServer> {
        return if (ZcashNetwork.fromResources(application).isMainnet()) {
            MainnetServer.allServers()
        } else {
            TestnetServer.allServers()
        }
    }

    fun updateSelectedServer(lightWalletServer: LightWalletServer?) {
        viewModelScope.launch(Dispatchers.IO) {
            val pref = EncryptedPreferenceSingleton.getInstance(application)
            EncryptedPreferenceKeys.SELECTED_SERVER.putValue(pref, lightWalletServer?.region ?: DEFAULT_REGION)
        }
    }

}

