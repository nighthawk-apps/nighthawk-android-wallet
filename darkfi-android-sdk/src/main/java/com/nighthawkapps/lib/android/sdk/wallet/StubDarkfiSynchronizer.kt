package com.nighthawkapps.lib.android.sdk.wallet

import android.content.Context
import com.nighthawkapps.lib.android.sdk.dao.DarkfiDaoProposalSummary
import com.nighthawkapps.lib.android.sdk.dao.DarkfiDaoSummary
import com.nighthawkapps.lib.android.sdk.wallet.rpc.DarkfidJsonRpc
import com.nighthawkapps.lib.android.sdk.wallet.rpc.DarkfidJsonRpcCaller
import com.nighthawkapps.lib.android.sdk.wallet.rpc.DarkfidLineJsonRpcCaller
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

class StubDarkfiSynchronizer(
    private val wallet: PersistableDarkfiWallet,
    private val darkfidRpc: DarkfidJsonRpcCaller,
) : DarkfiSynchronizer {
    /** Production path: SOCKS-aware JSON-RPC to [PersistableDarkfiWallet.endpoint]. */
    constructor(
        wallet: PersistableDarkfiWallet,
        applicationContext: Context,
    ) : this(
        wallet,
        DarkfidLineJsonRpcCaller(applicationContext.applicationContext, wallet.endpoint),
    )

    private val _status = MutableStateFlow(DarkfiSyncStatus.SYNCED)
    private val _processor =
        MutableStateFlow(DarkfiProcessorInfo(scannedBlocks = 1L, chainTip = 1L))
    private val _progress = MutableStateFlow(DarkfiPercent.HUNDRED_PERCENT)

    /** Stub balance (atomic DRK) until Rust backs sync via **`darkfid`** / **`darkfi-mobile-ffi`**. */
    private val _balanceAtomic = MutableStateFlow(10L * 100_000_000L)

    override val status: Flow<DarkfiSyncStatus> = _status.asStateFlow()
    override val processorInfo: Flow<DarkfiProcessorInfo> = _processor.asStateFlow()
    override val progress: Flow<DarkfiPercent> = _progress.asStateFlow()
    override val confirmedBalanceAtomic: Flow<Long> = _balanceAtomic.asStateFlow()
    override val transactions: Flow<List<DarkfiTransactionOverview>> = flowOf(emptyList())
    override val walletErrors: Flow<DarkfiWalletError?> = flowOf(null)

    override val supportsNativeTransfer: Boolean = false

    var closeCount: Int = 0
        private set

    override fun close() {
        closeCount++
    }

    override suspend fun getRecipients(tx: DarkfiTransactionOverview): List<DarkfiTransactionRecipient> = emptyList()

    override suspend fun estimateTransferFee(
        recipientAddress: String,
        amountDisplay: String,
        tokenId: String?,
        paymentMemo: String?,
    ): DarkfiTransferResult<Long> = nativeTransferUnavailable()

    override suspend fun submitTransfer(
        recipientAddress: String,
        amountDisplay: String,
        tokenId: String?,
        paymentMemo: String?,
    ): DarkfiTransferResult<String> = nativeTransferUnavailable()

    override suspend fun getTransactionPaymentMemo(txId: String): String? = null

    override suspend fun listDaos(): List<DarkfiDaoSummary> = emptyList()

    override suspend fun listProposals(daoName: String?): List<DarkfiDaoProposalSummary> = emptyList()

    override suspend fun getProposal(proposalBullaB58: String) = null

    private fun <T> nativeTransferUnavailable(): DarkfiTransferResult<T> =
        DarkfiTransferResult.Failure(
            "Native wallet library is unavailable. Install libdarkfi_mobile_ffi for this ABI.",
        )

    override fun walletAddresses(): DarkfiWalletAddresses = displayAddresses()

    override fun generateNewAddress(): String = displayAddresses().privateAddresses.first()

    override suspend fun refreshNow() {
        pingDarkfidBestEffort()

        _status.value = DarkfiSyncStatus.SYNCING
        _progress.value = DarkfiPercent(0.99f)
        _processor.value = DarkfiProcessorInfo(scannedBlocks = 1000L, chainTip = 1000L)
        _status.value = DarkfiSyncStatus.SYNCED
        _progress.value = DarkfiPercent.HUNDRED_PERCENT
    }

    private suspend fun pingDarkfidBestEffort() {
        runCatching {
            val pong =
                darkfidRpc.invoke(
                    DarkfidJsonRpc.requestObject(1, DarkfidJsonRpc.Method.PING),
                )
            DarkfidJsonRpc.resultPayloadOrThrow(pong)
        }
    }

    fun displayAddresses(): DarkfiWalletAddresses {
        val base =
            wallet.seedPhrase
                .joinToString(" ")
                .hashCode()
                .toULong()
                .toString(16)
        val primary = "drk_u_$base"
        return DarkfiWalletAddresses(
            privateAddresses = listOf(primary),
        )
    }
}
