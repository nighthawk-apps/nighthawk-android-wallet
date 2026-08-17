package com.nighthawkapps.lib.android.sdk.wallet

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Test double for [DarkfiSynchronizer] transfer paths. */
class FakeDarkfiSynchronizer(
    override val supportsNativeTransfer: Boolean = true,
    var estimateResult: DarkfiTransferResult<Long> = DarkfiTransferResult.Success(42L),
    var submitResult: DarkfiTransferResult<String> = DarkfiTransferResult.Success("tx-test"),
) : DarkfiSynchronizer {
    private val _balance = MutableStateFlow(0L)

    override val status: Flow<DarkfiSyncStatus> = MutableStateFlow(DarkfiSyncStatus.SYNCED).asStateFlow()
    override val processorInfo: Flow<DarkfiProcessorInfo> =
        MutableStateFlow(DarkfiProcessorInfo(0L, 0L)).asStateFlow()
    override val progress: Flow<DarkfiPercent> = MutableStateFlow(DarkfiPercent.HUNDRED_PERCENT).asStateFlow()
    override val confirmedBalanceAtomic: Flow<Long> = _balance.asStateFlow()
    override val transactions: Flow<List<DarkfiTransactionOverview>> =
        MutableStateFlow<List<DarkfiTransactionOverview>>(emptyList()).asStateFlow()
    override val walletErrors: Flow<DarkfiWalletError?> =
        MutableStateFlow<DarkfiWalletError?>(null).asStateFlow()

    var estimateCalls = 0
    var submitCalls = 0
    var closeCount = 0

    override fun close() {
        closeCount++
    }

    override suspend fun getRecipients(tx: DarkfiTransactionOverview): List<DarkfiTransactionRecipient> = emptyList()

    override fun walletAddresses() =
        DarkfiWalletAddresses(
            privateAddresses = listOf("drk_fake_private"),
        )

    override fun generateNewAddress(): String = "drk_new_fake_address"

    override suspend fun refreshNow() = Unit

    override suspend fun estimateTransferFee(
        recipientAddress: String,
        amountDisplay: String,
        tokenId: String?,
        paymentMemo: String?,
    ): DarkfiTransferResult<Long> {
        estimateCalls++
        return estimateResult
    }

    override suspend fun submitTransfer(
        recipientAddress: String,
        amountDisplay: String,
        tokenId: String?,
        paymentMemo: String?,
    ): DarkfiTransferResult<String> {
        submitCalls++
        return submitResult
    }

    override suspend fun getTransactionPaymentMemo(txId: String): String? = null
}
