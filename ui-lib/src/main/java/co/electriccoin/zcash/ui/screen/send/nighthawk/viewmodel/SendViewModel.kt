package co.electriccoin.zcash.ui.screen.send.nighthawk.viewmodel

import androidx.lifecycle.ViewModel
import cash.z.ecc.android.sdk.ext.ZcashSdk
import cash.z.ecc.android.sdk.ext.convertZecToZatoshi
import cash.z.ecc.android.sdk.ext.toZec
import cash.z.ecc.android.sdk.model.ZecSend
import cash.z.ecc.android.sdk.model.toZecString
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.screen.home.model.WalletSnapshot
import co.electriccoin.zcash.ui.screen.send.nighthawk.model.EnterZecUIState
import co.electriccoin.zcash.ui.screen.send.nighthawk.model.NumberPadValueTypes
import co.electriccoin.zcash.ui.screen.send.nighthawk.model.SendUIState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate

class SendViewModel: ViewModel() {
    private val _currentSendUiState = MutableStateFlow<SendUIState?>(SendUIState.ENTER_ZEC)
    val currentSendUIState: StateFlow<SendUIState?> get() = _currentSendUiState

    private val _enterZecUIState = MutableStateFlow(EnterZecUIState())
    val enterZecUIState: StateFlow<EnterZecUIState> get() = _enterZecUIState

    var userEnteredMemo: String = ""
        private set
    private var receiverAddress: String = ""
    var zecSend: ZecSend? = null
        private set

    fun onNextSendUiState() {
        _currentSendUiState.getAndUpdate { it?.getNext(it) }
    }

    fun onPreviousSendUiState() {
        _currentSendUiState.getAndUpdate { it?.getPrevious(it) }
    }

    fun onEnterMessageContinue(message: String) {
        userEnteredMemo = message
        onNextSendUiState()
    }

    fun onEnterReceiverAddressContinue(address: String, zecSend: ZecSend) {
        receiverAddress = address
        this.zecSend = zecSend
        onNextSendUiState()
    }

    fun onSendZCash() {
        onNextSendUiState()
    }

    fun onKeyPressed(numberPadValueTypes: NumberPadValueTypes) {
        Twig.info { "WrapAndroidSend: onKeyPressed $numberPadValueTypes" }
        when (numberPadValueTypes) {
            is NumberPadValueTypes.BackSpace -> onBackSpaceKeyPressed()
            is NumberPadValueTypes.Number -> onNumberKeyPressed(numberPadValueTypes.value)
            is NumberPadValueTypes.Separator -> onSeparatorKeyPressed(numberPadValueTypes.value)
        }
    }

    fun updateEnterZecUiStateWithWalletSnapshot(walletSnapshot: WalletSnapshot) {
        Twig.info { "SendVieModel walletSnapShot $walletSnapshot" }
        _enterZecUIState.getAndUpdate {
            val availableZatoshi = walletSnapshot.saplingBalance.available
            val isEnoughBalance = ((it.enteredAmount.toDoubleOrNull()?.toZec()?.convertZecToZatoshi()?.value ?: 0L) + ZcashSdk.MINERS_FEE.value) >= availableZatoshi.value
            it.copy(
                spendableBalance = availableZatoshi.toZecString(),
                isEnoughBalance = isEnoughBalance,
                isScanPaymentCodeOptionAvailable = it.enteredAmount == "0" && isEnoughBalance
            )
        }
    }

    private fun onBackSpaceKeyPressed() {
        _enterZecUIState.getAndUpdate {
            if (it.enteredAmount.isBlank().not() && it.enteredAmount != "0") {
                var newEnteredAmount = it.enteredAmount.dropLast(1)
                newEnteredAmount = newEnteredAmount.ifBlank { "0" }
                it.copy(
                    enteredAmount = newEnteredAmount
                )
            } else {
                it
            }
        }
    }

    private fun onNumberKeyPressed(value: String) {
        _enterZecUIState.getAndUpdate {
            var previousEnteredAmount = it.enteredAmount
            if (previousEnteredAmount == "0") {
                previousEnteredAmount = ""
            }
            val newEnteredAmount = previousEnteredAmount + value
            it.copy(
                enteredAmount = newEnteredAmount
            )
        }
    }

    private fun onSeparatorKeyPressed(value: String) {
        _enterZecUIState.getAndUpdate {
            if (it.enteredAmount.contains(value)) {
                it
            } else {
                it.copy(
                    enteredAmount = it.enteredAmount + value,
                    isScanPaymentCodeOptionAvailable = false
                )
            }
        }
    }
}
