package com.nighthawkapps.lib.android.ui.screen.transactiondetails.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiSynchronizer
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiTransactionRecipient
import com.nighthawkapps.lib.android.sdk.wallet.darkfiNetworkFromPackage
import com.nighthawkapps.lib.android.ui.common.ANDROID_STATE_FLOW_TIMEOUT
import com.nighthawkapps.lib.android.ui.preference.StandardPreferenceKeys
import com.nighthawkapps.lib.android.ui.preference.StandardPreferenceSingleton
import com.nighthawkapps.lib.android.ui.screen.transactiondetails.model.TransactionDetailsUIModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TransactionViewModel(
    application: Application
) : AndroidViewModel(application = application) {
    private val _transactionDetailsUiModel = MutableStateFlow<TransactionDetailsUIModel?>(null)
    val transactionDetailsUIModel: StateFlow<TransactionDetailsUIModel?> get() = _transactionDetailsUiModel

    fun getTransactionUiModel(
        transactionId: String,
        synchronizer: DarkfiSynchronizer
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            combine(synchronizer.transactions, synchronizer.processorInfo) { txs, info ->
                txs to info
            }.collectLatest { (list, info) ->
                val overview = list.find { it.rawId == transactionId } ?: return@collectLatest
                val recipient =
                    synchronizer.getRecipients(overview).firstOrNull()
                        ?: DarkfiTransactionRecipient(addressValue = "")
                _transactionDetailsUiModel.value =
                    TransactionDetailsUIModel(
                        transactionOverview = overview,
                        transactionRecipient = recipient,
                        network = darkfiNetworkFromPackage(getApplication()),
                        networkHeight = info.chainTip,
                        memo = "",
                    )
            }
        }
    }

    val isNavigateAwayFromWarningShown =
        flow {
            val preferenceProvider = StandardPreferenceSingleton.getInstance(application)
            emit(
                StandardPreferenceKeys.IS_NAVIGATE_AWAY_FROM_APP_WARNING_SHOWN.getValue(
                    preferenceProvider = preferenceProvider,
                ),
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT.inWholeMilliseconds),
            false,
        )

    fun updateNavigateAwayFromWaringFlag(isShown: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val preferenceProvider = StandardPreferenceSingleton.getInstance(getApplication())
            StandardPreferenceKeys.IS_NAVIGATE_AWAY_FROM_APP_WARNING_SHOWN.putValue(preferenceProvider, isShown)
        }
    }
}
