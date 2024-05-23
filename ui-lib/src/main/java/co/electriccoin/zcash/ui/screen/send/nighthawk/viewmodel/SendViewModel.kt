package co.electriccoin.zcash.ui.screen.send.nighthawk.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.SdkSynchronizer
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.ext.convertZatoshiToZecString
import cash.z.ecc.android.sdk.ext.convertZecToZatoshi
import cash.z.ecc.android.sdk.model.UnifiedSpendingKey
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.ZecSend
import cash.z.ecc.android.sdk.model.send
import cash.z.ecc.android.sdk.model.toZecString
import cash.z.ecc.android.sdk.type.AddressType
import co.electriccoin.zcash.preference.api.PreferenceProvider
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.MAXIMUM_FRACTION_DIGIT
import co.electriccoin.zcash.ui.common.UnsUtil
import co.electriccoin.zcash.ui.common.addressTypeNameId
import co.electriccoin.zcash.ui.common.removeTrailingZero
import co.electriccoin.zcash.ui.common.toBalanceValueModel
import co.electriccoin.zcash.ui.common.toFiatZatoshi
import co.electriccoin.zcash.ui.preference.StandardPreferenceKeys
import co.electriccoin.zcash.ui.preference.StandardPreferenceSingleton
import co.electriccoin.zcash.ui.screen.fiatcurrency.model.FiatCurrency
import co.electriccoin.zcash.ui.screen.fiatcurrency.model.FiatCurrencyUiState
import co.electriccoin.zcash.ui.screen.home.model.WalletSnapshot
import co.electriccoin.zcash.ui.screen.home.model.spendableBalance
import co.electriccoin.zcash.ui.screen.send.nighthawk.model.EnterZecUIState
import co.electriccoin.zcash.ui.screen.send.nighthawk.model.NumberPadValueTypes
import co.electriccoin.zcash.ui.screen.send.nighthawk.model.SendAndReviewUiState
import co.electriccoin.zcash.ui.screen.send.nighthawk.model.SendConfirmationState
import co.electriccoin.zcash.ui.screen.send.nighthawk.model.SendUIState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SendViewModel(val context: Application) : AndroidViewModel(application = context) {
    private val _currentSendUiState = MutableStateFlow<SendUIState?>(SendUIState.ENTER_ZEC)
    val currentSendUIState: StateFlow<SendUIState?> get() = _currentSendUiState

    private val _enterZecUIState = MutableStateFlow(EnterZecUIState())
    val enterZecUIState: StateFlow<EnterZecUIState> get() = _enterZecUIState

    private val _sendConfirmationState =
        MutableStateFlow<SendConfirmationState>(SendConfirmationState.Sending)
    val sendConfirmationState: StateFlow<SendConfirmationState> get() = _sendConfirmationState

    var userEnteredMemo: String = ""
        private set
    var receiverAddress: String = ""
        private set
    var zecSend: ZecSend? = null
        private set

    private val uns by lazy { UnsUtil() }
    private var prefProvider: PreferenceProvider? = null

    private var fiatCurrencyUiState: FiatCurrencyUiState =
        FiatCurrencyUiState(FiatCurrency.OFF, null)
    private var isFiatCurrencyPreferredOverZec: Boolean = false

    fun onNextSendUiState() {
        _currentSendUiState.getAndUpdate { it?.getNext(it) }
    }

    fun onPreviousSendUiState() {
        _currentSendUiState.getAndUpdate { it?.getPrevious(it) }
    }

    fun onEnterMessageContinue(message: String) {
        updateMemo(message)
        onNextSendUiState()
    }

    fun onEnterReceiverAddressContinue(address: String, zecSend: ZecSend) {
        updateReceiverAddress(address)
        this.zecSend = zecSend
        onNextSendUiState()
    }

    fun onSendZCash(
        zecSend: ZecSend?,
        spendingKey: UnifiedSpendingKey?,
        synchronizer: Synchronizer?
    ) {
        onNextSendUiState()
        initiateSend(zecSend, spendingKey, synchronizer)
    }

    fun updateReceiverAddress(address: String) {
        receiverAddress = address
    }

    fun updateMemo(memo: String) {
        userEnteredMemo = memo
    }

    fun enteredZecFromDeepLink(zec: String) {
        _enterZecUIState.update {
            it.copy(
                enteredAmount = zec.toDoubleOrNull().convertZecToZatoshi().toBalanceValueModel(
                    fiatCurrencyUiState,
                    isFiatCurrencyPreferredOverZec
                ).balance
            )
        }
    }

    fun updateFiatCurrencyData(
        fiatCurrencyUiState: FiatCurrencyUiState,
        isFiatCurrencyPreferred: Boolean
    ) {
        this.fiatCurrencyUiState = fiatCurrencyUiState
        isFiatCurrencyPreferredOverZec = isFiatCurrencyPreferred
    }

    fun getEnteredAmountInZecString(): String {
        return enterZecUIState.value.enteredAmount.toDoubleOrNull()?.toFiatZatoshi(fiatCurrencyUiState, isFiatCurrencyPreferredOverZec)
            .convertZatoshiToZecString(MAXIMUM_FRACTION_DIGIT)
    }

    fun onKeyPressed(numberPadValueTypes: NumberPadValueTypes) {
        Twig.debug { "WrapAndroidSend: onKeyPressed $numberPadValueTypes" }
        when (numberPadValueTypes) {
            is NumberPadValueTypes.BackSpace -> onBackSpaceKeyPressed()
            is NumberPadValueTypes.Number -> onNumberKeyPressed(numberPadValueTypes.value)
            is NumberPadValueTypes.Separator -> onSeparatorKeyPressed(numberPadValueTypes.value)
        }
    }

    private fun updateSendConfirmationState(sendConfirmationState: SendConfirmationState) {
        _sendConfirmationState.update { sendConfirmationState }
    }

    fun updateEnterZecUiStateWithWalletSnapshot(walletSnapshot: WalletSnapshot) {
        Twig.debug { "SendVieModel walletSnapShot $walletSnapshot" }
        _enterZecUIState.getAndUpdate {
            val availableZatoshi = walletSnapshot.spendableBalance()
            val balanceValuesModel = (it.enteredAmount.toDoubleOrNull()
                ?.toFiatZatoshi(fiatCurrencyUiState, isFiatCurrencyPreferredOverZec)
                ?: Zatoshi(0)).toBalanceValueModel(
                fiatCurrencyUiState,
                isFiatCurrencyPreferredOverZec
            )
            val availableBalanceModel = availableZatoshi.toBalanceValueModel(
                fiatCurrencyUiState,
                isFiatCurrencyPreferredOverZec
            )
            val isEnoughBalance = ((availableBalanceModel.balance.toDoubleOrNull() ?: 0.0) > 0) &&
                    ((it.enteredAmount.toDoubleOrNull() ?: 0.0) <= (availableBalanceModel.balance.toDoubleOrNull() ?: 0.0))
            it.copy(
                amountUnit = balanceValuesModel.balanceUnit,
                spendableBalance = availableBalanceModel.balance,
                fiatAmount = balanceValuesModel.fiatBalance,
                fiatUnit = balanceValuesModel.fiatUnit,
                isEnoughBalance = isEnoughBalance,
                isScanPaymentCodeOptionAvailable = it.enteredAmount == "0" && isEnoughBalance
            )
        }
    }

    fun switchEnteredAmountType() {
        _enterZecUIState.update {
            it.copy(
                enteredAmount = it.fiatAmount ?: "0",
                amountUnit = it.fiatUnit ?: "ZEC",
                fiatAmount = it.enteredAmount,
                fiatUnit = it.amountUnit
            )
        }
    }

    fun onSendAllClicked(enteredAmount: String) {
        _enterZecUIState.update {
            it.copy(enteredAmount = enteredAmount)
        }
    }

    fun sendAndReviewUiState() = run {
        val balanceValuesModel = (zecSend?.amount ?: Zatoshi(0)).toBalanceValueModel(fiatCurrencyUiState, isFiatCurrencyPreferredOverZec)
        SendAndReviewUiState()
            .copy(
                amountToSend = balanceValuesModel.balance,
                amountUnit = balanceValuesModel.balanceUnit,
                convertedAmountWithCurrency = "${balanceValuesModel.fiatBalance} ${balanceValuesModel.fiatUnit}",
                memo = zecSend?.memo?.value ?: "",
                recipientType = context.getString(
                    (zecSend?.destination?.address ?: "").addressTypeNameId()
                ),
                receiverAddress = zecSend?.destination?.address ?: "",
                subTotal = zecSend?.amount?.toZecString()?.removeTrailingZero() ?: "",
                networkFees = "",
                totalAmount = zecSend?.amount?.toZecString()?.removeTrailingZero() ?: ""
            )
    }

    private fun initiateSend(
        zecSend: ZecSend?,
        spendingKey: UnifiedSpendingKey?,
        synchronizer: Synchronizer?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (zecSend == null) {
                Twig.error { "Sending Zec: Send zec is null" }
                updateSendConfirmationState(SendConfirmationState.Failed)
                return@launch
            }
            if (spendingKey == null) {
                Twig.error { "Sending Zec: spending key is null" }
                updateSendConfirmationState(SendConfirmationState.Failed)
                return@launch
            }
            if (synchronizer == null) {
                Twig.error { "Sending Zec: synchronizer is null" }
                updateSendConfirmationState(SendConfirmationState.Failed)
                return@launch
            }
            runCatching {
                synchronizer.send(spendingKey = spendingKey, send = zecSend)
            }
                .onSuccess {
                    Twig.debug { "Sending Zec: Sent successfully $it" }
                    updateSendConfirmationState(SendConfirmationState.Success)
                    (synchronizer as? SdkSynchronizer?)?.run {
                        refreshTransactions()
                        refreshAllBalances()
                    }
                }
                .onFailure {
                    Twig.error { "Sending Zec: Send fail $it" }
                    updateSendConfirmationState(SendConfirmationState.Failed)
                }
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

    fun clearViewModelSavedData() {
        fiatCurrencyUiState = FiatCurrencyUiState(FiatCurrency.OFF, null)
        isFiatCurrencyPreferredOverZec = false
        _currentSendUiState.value = SendUIState.ENTER_ZEC
        _enterZecUIState.value = EnterZecUIState()
        zecSend = null
        updateMemo("")
        updateReceiverAddress("")
        resetSendConfirmationState()
    }

    fun resetSendConfirmationState() {
        updateSendConfirmationState(SendConfirmationState.Sending)
    }

    private suspend fun getSharedPrefProvider(): PreferenceProvider {
        if (prefProvider == null) {
            prefProvider = StandardPreferenceSingleton.getInstance(context)
        }
        return prefProvider as PreferenceProvider
    }

    suspend fun validateAddress(address: String, synchronizer: Synchronizer): AddressType {
        var addressType = synchronizer.validateAddress(address)
        if (addressType.isNotValid) {
            if (StandardPreferenceKeys.IS_UNSTOPPABLE_SERVICE_ENABLED.getValue(getSharedPrefProvider())) {
                runCatching {
                    uns.isValidUNSAddress(address)
                }.onSuccess { unsAddress ->
                    if (unsAddress != null) {
                        receiverAddress = unsAddress
                        addressType = synchronizer.validateAddress(unsAddress)
                    }
                }.onFailure {
                    Twig.debug { "Error in validating unstoppable address $it" }
                }
            }
        }
        return addressType
    }
}
