package co.electriccoin.zcash.ui.screen.send.nighthawk.viewmodel

import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.spackle.Twig
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

    private var userEnteredMemo: String = ""
    private var receiverAddress: String = ""

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

    fun onEnterReceiverAddressContinue(address: String) {
        receiverAddress = address
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

    private fun onBackSpaceKeyPressed() {
        _enterZecUIState.getAndUpdate {
            if (it.enteredAmount.isBlank().not() && it.enteredAmount != "0") {
                var newEnteredAmount = it.enteredAmount.dropLast(1)
                newEnteredAmount = newEnteredAmount.ifBlank { "0" }
                it.copy(
                    enteredAmount = newEnteredAmount,
                    isEnoughBalance = newEnteredAmount.toDoubleOrNull()?.let {num -> num < 10 } ?: false,
                    isScanPaymentCodeOptionAvailable = newEnteredAmount == "0"
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
                enteredAmount = newEnteredAmount,
                spendableBalance = "10",
                isEnoughBalance = newEnteredAmount.toDoubleOrNull()?.let {num -> num < 10 } ?: false,
                isScanPaymentCodeOptionAvailable = false
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
