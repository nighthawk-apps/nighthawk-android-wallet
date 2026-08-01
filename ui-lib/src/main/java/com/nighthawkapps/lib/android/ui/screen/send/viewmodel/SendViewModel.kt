@file:Suppress("LongMethod", "ReturnCount")

package com.nighthawkapps.lib.android.ui.screen.send.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nighthawkapps.lib.android.global.AppWalletCoordinator
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiAmountFormatter
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiSynchronizer
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiTransferResult
import com.nighthawkapps.lib.android.sdk.wallet.SendBalanceCheck
import com.nighthawkapps.lib.android.sdk.wallet.evaluateSendBalance
import com.nighthawkapps.lib.android.sdk.wallet.validateAddressStub
import com.nighthawkapps.lib.android.ui.common.ANDROID_STATE_FLOW_TIMEOUT
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SendViewModel(
    application: Application,
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
        val sync = synchronizer.value
        if (sync == null) {
            _errorMessage.value = "Wallet is not ready"
            return
        }
        if (!sync.supportsNativeTransfer) {
            _errorMessage.value = "Native wallet is not available on this device"
            return
        }

        when (val balanceCheck = evaluateBalance(confirmedBalanceAtomic, amountDisplay)) {
            SendBalanceCheck.FeePending -> {
                _errorMessage.value = "Waiting for fee estimate — try again in a moment"
                return
            }

            is SendBalanceCheck.Insufficient -> {
                _errorMessage.value = insufficientBalanceMessage(balanceCheck)
                return
            }

            SendBalanceCheck.Ok -> {
                Unit
            }
        }

        viewModelScope.launch {
            _isSubmitting.value = true
            _errorMessage.value = null
            _lastTxId.value = null

            when (val balanceCheck = evaluateBalance(confirmedBalanceAtomic, amountDisplay)) {
                SendBalanceCheck.FeePending -> {
                    _errorMessage.value = "Waiting for fee estimate — try again in a moment"
                    _isSubmitting.value = false
                    return@launch
                }

                is SendBalanceCheck.Insufficient -> {
                    _errorMessage.value = insufficientBalanceMessage(balanceCheck)
                    _isSubmitting.value = false
                    return@launch
                }

                SendBalanceCheck.Ok -> {
                    Unit
                }
            }

            val addressType = sync.validateAddressStub(recipientAddress.trim())
            if (addressType == com.nighthawkapps.lib.android.sdk.wallet.DarkfiAddressType.Invalid) {
                _errorMessage.value = "Invalid recipient address"
                _isSubmitting.value = false
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
                    _lastTxId.value = result.value
                    walletCoordinator.rescanBlockchain()
                    onSuccess()
                }

                is DarkfiTransferResult.Failure -> {
                    _errorMessage.value = result.message
                }
            }
            _isSubmitting.value = false
        }
    }

    fun formatFeeAtomic(feeAtomic: Long?): String? = feeAtomic?.let { DarkfiAmountFormatter.formatAtomic(it) }
}
