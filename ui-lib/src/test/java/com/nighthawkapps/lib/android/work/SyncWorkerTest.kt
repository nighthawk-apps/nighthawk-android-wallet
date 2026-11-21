package com.nighthawkapps.lib.android.work

import androidx.work.ListenableWorker
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiAddressType
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiPercent
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiProcessorInfo
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiSyncStatus
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiSynchronizer
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiTransactionOverview
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiTransactionRecipient
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiTransferResult
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiWalletAddresses
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiWalletError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncWorkerTest {
    @Test
    fun nullSynchronizer_returnsRetryImmediately() =
        runTest {
            val flow = MutableStateFlow<DarkfiSynchronizer?>(null)
            val result = SyncWorker.runSyncAttempt(flow, synchronizerWaitMs = 50L, syncTimeoutMs = 200L)
            assertEquals(ListenableWorker.Result.retry(), result)
        }

    @Test
    fun errorStatus_returnsRetry() =
        runTest {
            val sync = TestSynchronizer(DarkfiSyncStatus.ERROR, DarkfiPercent.ZERO_PERCENT, chainTip = 100L)
            val result =
                SyncWorker.runSyncAttempt(
                    MutableStateFlow(sync),
                    synchronizerWaitMs = 50L,
                    syncTimeoutMs = 200L,
                )
            assertEquals(ListenableWorker.Result.retry(), result)
        }

    @Test
    fun degradedStatus_returnsRetry() =
        runTest {
            val sync = TestSynchronizer(DarkfiSyncStatus.DEGRADED, DarkfiPercent.ZERO_PERCENT, chainTip = 100L)
            val result =
                SyncWorker.runSyncAttempt(
                    MutableStateFlow(sync),
                    synchronizerWaitMs = 50L,
                    syncTimeoutMs = 200L,
                )
            assertEquals(ListenableWorker.Result.retry(), result)
        }

    @Test
    fun protoMismatch_returnsFailure() =
        runTest {
            val sync = TestSynchronizer(DarkfiSyncStatus.PROTO_MISMATCH, DarkfiPercent.ZERO_PERCENT, chainTip = 100L)
            val result =
                SyncWorker.runSyncAttempt(
                    MutableStateFlow(sync),
                    synchronizerWaitMs = 50L,
                    syncTimeoutMs = 200L,
                )
            assertEquals(ListenableWorker.Result.failure(), result)
        }

    @Test
    fun syncedWithZeroChainTip_returnsRetry() =
        runTest {
            val sync = TestSynchronizer(DarkfiSyncStatus.SYNCED, DarkfiPercent.HUNDRED_PERCENT, chainTip = 0L)
            val result =
                SyncWorker.runSyncAttempt(
                    MutableStateFlow(sync),
                    synchronizerWaitMs = 50L,
                    syncTimeoutMs = 200L,
                )
            assertEquals(ListenableWorker.Result.retry(), result)
        }

    @Test
    fun syncedWithPositiveChainTipAndHundredPercent_returnsSuccess() =
        runTest {
            val sync = TestSynchronizer(DarkfiSyncStatus.SYNCED, DarkfiPercent.HUNDRED_PERCENT, chainTip = 500L)
            val result =
                SyncWorker.runSyncAttempt(
                    MutableStateFlow(sync),
                    synchronizerWaitMs = 50L,
                    syncTimeoutMs = 200L,
                )
            assertEquals(ListenableWorker.Result.success(), result)
        }

    @Test
    fun stuckSyncing_timesOutAndReturnsRetry() =
        runTest {
            val sync = TestSynchronizer(DarkfiSyncStatus.SYNCING, DarkfiPercent(0.5f), chainTip = 500L)
            val result =
                SyncWorker.runSyncAttempt(
                    MutableStateFlow(sync),
                    synchronizerWaitMs = 50L,
                    syncTimeoutMs = 50L,
                )
            assertEquals(ListenableWorker.Result.retry(), result)
        }

    private class TestSynchronizer(
        initialStatus: DarkfiSyncStatus,
        initialProgress: DarkfiPercent,
        chainTip: Long,
    ) : DarkfiSynchronizer {
        override val status: Flow<DarkfiSyncStatus> = MutableStateFlow(initialStatus).asStateFlow()
        override val processorInfo: Flow<DarkfiProcessorInfo> =
            MutableStateFlow(DarkfiProcessorInfo(scannedBlocks = chainTip, chainTip = chainTip)).asStateFlow()
        override val progress: Flow<DarkfiPercent> = MutableStateFlow(initialProgress).asStateFlow()
        override val confirmedBalanceAtomic: Flow<Long> = MutableStateFlow(0L).asStateFlow()
        override val transactions: Flow<List<DarkfiTransactionOverview>> =
            MutableStateFlow<List<DarkfiTransactionOverview>>(emptyList()).asStateFlow()
        override val walletErrors: Flow<DarkfiWalletError?> = MutableStateFlow<DarkfiWalletError?>(null).asStateFlow()

        override fun close() = Unit

        override suspend fun getRecipients(tx: DarkfiTransactionOverview,): List<DarkfiTransactionRecipient> = emptyList()

        override fun walletAddresses(): DarkfiWalletAddresses = DarkfiWalletAddresses(emptyList())

        override fun generateNewAddress(): String = ""

        override suspend fun refreshNow() = Unit

        override suspend fun estimateTransferFee(
            recipientAddress: String,
            amountDisplay: String,
            tokenId: String?,
            paymentMemo: String?,
        ): DarkfiTransferResult<Long> = DarkfiTransferResult.Success(0L)

        override suspend fun submitTransfer(
            recipientAddress: String,
            amountDisplay: String,
            tokenId: String?,
            paymentMemo: String?,
        ): DarkfiTransferResult<String> = DarkfiTransferResult.Success("")

        override val supportsNativeTransfer: Boolean = false

        override suspend fun getTransactionPaymentMemo(txId: String): String? = null
    }
}
