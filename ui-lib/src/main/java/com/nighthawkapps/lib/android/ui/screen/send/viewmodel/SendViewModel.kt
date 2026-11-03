@file:Suppress("LongMethod", "ReturnCount", "CyclomaticComplexMethod", "LongParameterList", "TooManyFunctions")

package com.nighthawkapps.lib.android.ui.screen.send.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.nighthawkapps.lib.android.global.AppWalletCoordinator
import com.nighthawkapps.lib.android.global.DeepLinkUtil
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiAmountFormatter
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiPaymentMemo
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiSynchronizer
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiTransferResult
import com.nighthawkapps.lib.android.sdk.wallet.SendBalanceCheck
import com.nighthawkapps.lib.android.sdk.wallet.evaluateSendBalance
import com.nighthawkapps.lib.android.sdk.wallet.validateAddressStub
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.ANDROID_STATE_FLOW_TIMEOUT
import com.nighthawkapps.lib.android.ui.screen.send.model.SendStage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SendViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val walletCoordinator = AppWalletCoordinator.get(application)

    val synchronizer: StateFlow<DarkfiSynchronizer?> =
        walletCoordinator.synchronizer.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT.inWholeMilliseconds),
            null,
        )

    private val _feeAtomic = MutableStateFlow<Long?>(null)
    val feeAtomic: StateFlow<Long?> = _feeAtomic

    private val _isEstimatingFee = MutableStateFlow(false)
    val isEstimatingFee: StateFlow<Boolean> = _isEstimatingFee

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _lastTxId = MutableStateFlow<String?>(null)
    val lastTxId: StateFlow<String?> = _lastTxId

    private var feeJob: Job? = null
    private var submitGeneration = 0

    val stage: StateFlow<String> = savedStateHandle.getStateFlow(KEY_STAGE, SendStage.Amount.name)

    init {
        if (savedStateHandle.get<String>(KEY_STAGE) == SendStage.Sending.name) {
            savedStateHandle[KEY_STAGE] = SendStage.Failed.name
            _errorMessage.value = application.getString(R.string.ns_send_status_unknown)
        }
    }

    val amountText: StateFlow<String> = savedStateHandle.getStateFlow(KEY_AMOUNT, "")

    val recipient: StateFlow<String> = savedStateHandle.getStateFlow(KEY_RECIPIENT, "")

    val memoText: StateFlow<String> = savedStateHandle.getStateFlow(KEY_MEMO, "")

    val selectedTokenId: StateFlow<String> = savedStateHandle.getStateFlow(KEY_TOKEN, "")

    fun setStage(stage: SendStage) {
        savedStateHandle[KEY_STAGE] = stage.name
    }

    fun setAmountText(value: String) {
        savedStateHandle[KEY_AMOUNT] = value
    }

    fun setRecipient(value: String) {
        savedStateHandle[KEY_RECIPIENT] = value.trim()
    }

    fun setMemoText(value: String) {
        savedStateHandle[KEY_MEMO] = DarkfiPaymentMemo.truncateToMaxBytes(value)
    }

    fun setSelectedTokenId(tokenId: String?) {
        savedStateHandle[KEY_TOKEN] = tokenId.orEmpty()
    }

    fun selectTokenIfUnset(tokenId: String) {
        if (savedStateHandle.get<String>(KEY_TOKEN).isNullOrBlank()) {
            savedStateHandle[KEY_TOKEN] = tokenId
        }
    }

    /**
     * Merge a scan or navigation argument into the draft. Null fields are left
     * alone so a QR scan does not wipe an amount or memo the user already typed.
     * Stage advances to Review only when this payload itself includes an amount.
     */
    fun applyIncoming(
        recipientAddress: String? = null,
        amountDisplay: String? = null,
        memo: String? = null,
        tokenId: String? = null,
    ) {
        val recipientValue = recipientAddress?.trim()?.takeIf { it.isNotBlank() }
        val amountValue = amountDisplay?.trim()?.takeIf { it.isNotBlank() }
        var applied = false
        if (recipientValue != null) {
            savedStateHandle[KEY_RECIPIENT] = recipientValue
            applied = true
        }
        if (amountValue != null) {
            savedStateHandle[KEY_AMOUNT] = amountValue
            applied = true
        }
        if (memo != null) {
            savedStateHandle[KEY_MEMO] = DarkfiPaymentMemo.truncateToMaxBytes(memo)
            applied = true
        }
        if (!tokenId.isNullOrBlank()) {
            savedStateHandle[KEY_TOKEN] = tokenId
            applied = true
        }
        if (!applied) {
            return
        }
        val next =
            when {
                recipientValue != null && amountValue != null -> {
                    SendStage.Review.name
                }

                recipientValue != null -> {
                    SendStage.Recipient.name
                }

                else -> {
                    savedStateHandle.get<String>(KEY_STAGE) ?: SendStage.Amount.name
                }
            }
        savedStateHandle[KEY_STAGE] = next
    }

    fun applyDeepLink(data: DeepLinkUtil.SendDeepLinkData) {
        applyIncoming(
            recipientAddress = data.address,
            amountDisplay = data.amount?.let { DarkfiAmountFormatter.formatAtomic(it) },
            memo = data.memo,
        )
    }

    fun onFlowExit() {
        submitGeneration++
        savedStateHandle[KEY_STAGE] = SendStage.Amount.name
    }

    fun clearDraft() {
        savedStateHandle[KEY_STAGE] = SendStage.Amount.name
        savedStateHandle[KEY_AMOUNT] = ""
        savedStateHandle[KEY_RECIPIENT] = ""
        savedStateHandle[KEY_MEMO] = ""
        savedStateHandle[KEY_TOKEN] = ""
    }

    fun clearTransientState() {
        _errorMessage.value = null
        _lastTxId.value = null
    }

    fun estimateFee(
        recipientAddress: String,
        amountDisplay: String,
        tokenId: String? = null,
        paymentMemo: String? = null,
    ) {
        feeJob?.cancel()
        _feeAtomic.value = null
        _errorMessage.value = null

        val sync = synchronizer.value
        if (sync == null || !sync.supportsNativeTransfer) {
            _feeAtomic.value = null
            return
        }

        feeJob =
            viewModelScope.launch {
                _isEstimatingFee.value = true
                when (
                    val result =
                        sync.estimateTransferFee(
                            recipientAddress = recipientAddress,
                            amountDisplay = amountDisplay,
                            tokenId = tokenId,
                            paymentMemo = paymentMemo,
                        )
                ) {
                    is DarkfiTransferResult.Success -> {
                        _feeAtomic.value = result.value
                    }

                    is DarkfiTransferResult.Failure -> {
                        _errorMessage.value = result.message
                    }
                }
                _isEstimatingFee.value = false
            }
    }

    fun evaluateBalance(
        confirmedBalanceAtomic: Long,
        amountDisplay: String,
    ): SendBalanceCheck =
        evaluateSendBalance(
            confirmedBalanceAtomic = confirmedBalanceAtomic,
            amountDisplay = amountDisplay,
            feeAtomic = _feeAtomic.value,
        )

    fun insufficientBalanceMessage(check: SendBalanceCheck.Insufficient): String {
        val shortfall =
            (check.requiredAtomic - check.availableAtomic).coerceAtLeast(0L)
        return buildString {
            append("Insufficient balance. You need ")
            append(DarkfiAmountFormatter.formatAtomic(check.requiredAtomic))
            append(" DRK (amount + fee) but only have ")
            append(DarkfiAmountFormatter.formatAtomic(check.availableAtomic))
            append(" DRK. Add at least ")
            append(DarkfiAmountFormatter.formatAtomic(shortfall))
            append(" DRK more to complete this transfer.")
        }
    }

    fun submitTransfer(
        recipientAddress: String,
        amountDisplay: String,
        confirmedBalanceAtomic: Long,
        tokenId: String? = null,
        paymentMemo: String? = null,
        onSuccess: () -> Unit,
    ) {
        if (_isSubmitting.value) return
        _isSubmitting.value = true
        val generation = submitGeneration
        val sync = synchronizer.value
        if (sync == null) {
            _errorMessage.value = "Wallet is not ready"
            _isSubmitting.value = false
            return
        }
        if (!sync.supportsNativeTransfer) {
            _errorMessage.value = "Native wallet is not available on this device"
            _isSubmitting.value = false
            return
        }

        when (val balanceCheck = evaluateBalance(confirmedBalanceAtomic, amountDisplay)) {
            SendBalanceCheck.FeePending -> {
                _errorMessage.value = "Waiting for fee estimate — try again in a moment"
                _isSubmitting.value = false
                return
            }

            is SendBalanceCheck.Insufficient -> {
                _errorMessage.value = insufficientBalanceMessage(balanceCheck)
                _isSubmitting.value = false
                return
            }

            SendBalanceCheck.Ok -> {
                Unit
            }
        }

        _errorMessage.value = null
        _lastTxId.value = null
        viewModelScope.launch {
            try {
                if (generation != submitGeneration) return@launch
                when (val balanceCheck = evaluateBalance(confirmedBalanceAtomic, amountDisplay)) {
                    SendBalanceCheck.FeePending -> {
                        _errorMessage.value = "Waiting for fee estimate — try again in a moment"
                        return@launch
                    }

                    is SendBalanceCheck.Insufficient -> {
                        _errorMessage.value = insufficientBalanceMessage(balanceCheck)
                        return@launch
                    }

                    SendBalanceCheck.Ok -> {
                        Unit
                    }
                }

                val addressType = sync.validateAddressStub(recipientAddress.trim())
                if (addressType == com.nighthawkapps.lib.android.sdk.wallet.DarkfiAddressType.Invalid) {
                    _errorMessage.value = "Invalid recipient address"
                    return@launch
                }

                when (
                    val result =
                        sync.submitTransfer(
                            recipientAddress = recipientAddress,
                            amountDisplay = amountDisplay,
                            tokenId = tokenId,
                            paymentMemo = paymentMemo,
                        )
                ) {
                    is DarkfiTransferResult.Success -> {
                        if (generation != submitGeneration) return@launch
                        _lastTxId.value = result.value
                        walletCoordinator.rescanBlockchain()
                        onSuccess()
                    }

                    is DarkfiTransferResult.Failure -> {
                        if (generation == submitGeneration) {
                            _errorMessage.value = result.message
                        }
                    }
                }
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    fun formatFeeAtomic(feeAtomic: Long?): String? = feeAtomic?.let { DarkfiAmountFormatter.formatAtomic(it) }

    companion object {
        private const val KEY_STAGE = "send_stage"
        private const val KEY_AMOUNT = "send_amount"
        private const val KEY_RECIPIENT = "send_recipient"
        private const val KEY_MEMO = "send_memo"
        private const val KEY_TOKEN = "send_token_id"
    }
}
