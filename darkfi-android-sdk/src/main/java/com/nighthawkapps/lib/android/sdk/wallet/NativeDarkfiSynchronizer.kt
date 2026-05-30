package com.nighthawkapps.lib.android.sdk.wallet

import android.content.Context
import com.nighthawkapps.lib.android.sdk.uniffi.DarkfiMobileFfiApi
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.DarkfiWalletHandle
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.DarkfiWalletNativeException
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.DrkSyncSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * [DarkfiSynchronizer] backed by UniFFI **`DarkfiWalletHandle`** (on-device **`drk`**).
 */
class NativeDarkfiSynchronizer internal constructor(
    private val wallet: PersistableDarkfiWallet,
    private val handle: DarkfiWalletHandle,
) : DarkfiSynchronizer {
    constructor(
        wallet: PersistableDarkfiWallet,
        applicationContext: Context,
    ) : this(
        wallet,
        DarkfiMobileFfiApi.openWallet(applicationContext.applicationContext, wallet),
    )

    private val _status = MutableStateFlow(DarkfiSyncStatus.SYNCING)
    private val _processor =
        MutableStateFlow(DarkfiProcessorInfo(scannedBlocks = 0L, chainTip = 0L))
    private val _progress = MutableStateFlow(DarkfiPercent(0f))
    private val _balanceAtomic = MutableStateFlow(0L)
    private val _transactions = MutableStateFlow<List<DarkfiTransactionOverview>>(emptyList())
    private val _walletErrors = MutableStateFlow<DarkfiWalletError?>(null)

    init {
        refreshBalanceBestEffort()
        refreshTransactionsBestEffort()
        applySyncSnapshotBestEffort()
        _status.value = DarkfiSyncStatus.SYNCED
    }

    override val status: Flow<DarkfiSyncStatus> = _status.asStateFlow()
    override val processorInfo: Flow<DarkfiProcessorInfo> = _processor.asStateFlow()
    override val progress: Flow<DarkfiPercent> = _progress.asStateFlow()
    override val confirmedBalanceAtomic: Flow<Long> = _balanceAtomic.asStateFlow()
    override val transactions: Flow<List<DarkfiTransactionOverview>> = _transactions.asStateFlow()
    override val walletErrors: Flow<DarkfiWalletError?> = _walletErrors.asStateFlow()

    override val supportsNativeTransfer: Boolean = true

    override suspend fun getRecipients(tx: DarkfiTransactionOverview): List<DarkfiTransactionRecipient> = emptyList()

    override suspend fun estimateTransferFee(
        recipientAddress: String,
        amountDisplay: String,
        tokenId: String?,
    ): DarkfiTransferResult<Long> =
        withContext(Dispatchers.IO) {
            when (val recipient = DarkfiTransferSupport.validateRecipient(recipientAddress)) {
                is DarkfiTransferResult.Failure -> {
                    recipient
                }

                is DarkfiTransferResult.Success -> {
                    when (val amount = DarkfiTransferSupport.parseAmountDisplay(amountDisplay)) {
                        is DarkfiTransferResult.Failure -> {
                            amount
                        }

                        is DarkfiTransferResult.Success -> {
                            runCatching {
                                handle.estimateTransferFee(
                                    recipient.value,
                                    amount.value,
                                    tokenId,
                                )
                            }.fold(
                                onSuccess = { DarkfiTransferResult.Success(it) },
                                onFailure = { e ->
                                    DarkfiTransferResult.Failure(
                                        e.message ?: "Fee estimate failed",
                                        e,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

    override suspend fun submitTransfer(
        recipientAddress: String,
        amountDisplay: String,
        tokenId: String?,
    ): DarkfiTransferResult<String> =
        withContext(Dispatchers.IO) {
            when (val recipient = DarkfiTransferSupport.validateRecipient(recipientAddress)) {
                is DarkfiTransferResult.Failure -> {
                    recipient
                }

                is DarkfiTransferResult.Success -> {
                    when (val amount = DarkfiTransferSupport.parseAmountDisplay(amountDisplay)) {
                        is DarkfiTransferResult.Failure -> {
                            amount
                        }

                        is DarkfiTransferResult.Success -> {
                            runCatching {
                                val txBytes =
                                    handle.buildTransfer(
                                        recipient.value,
                                        amount.value,
                                        tokenId,
                                    )
                                handle.broadcastTransfer(txBytes)
                            }.fold(
                                onSuccess = { txid ->
                                    refreshBalanceBestEffort()
                                    refreshTransactionsBestEffort()
                                    DarkfiTransferResult.Success(txid)
                                },
                                onFailure = { e ->
                                    DarkfiTransferResult.Failure(
                                        e.message ?: "Transfer failed",
                                        e,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

    override fun walletAddresses(): DarkfiWalletAddresses =
        runCatching { handle.listAddresses() }
            .map { addrs ->
                DarkfiWalletAddresses(
                    privateAddresses = addrs,
                )
            }.getOrElse {
                StubDarkfiSynchronizer(wallet, NoOpDarkfidRpc).displayAddresses()
            }

    override fun generateNewAddress(): String = handle.generateNewAddress()

    override suspend fun refreshNow() {
        _status.value = DarkfiSyncStatus.SYNCING
        runCatching { handle.refreshNow() }
            .onSuccess { applySyncSnapshot(it) }
            .onFailure { e ->
                _walletErrors.value =
                    DarkfiWalletError.Processor(
                        if (e is DarkfiWalletNativeException) e else e,
                    )
            }
        refreshBalanceBestEffort()
        refreshTransactionsBestEffort()
        _status.value = DarkfiSyncStatus.SYNCED
    }

    private fun refreshTransactionsBestEffort() {
        runCatching { handle.listTransactions() }
            .onSuccess { _transactions.value = DrkTransactionMapping.overviews(it) }
            .onFailure { /* keep last known list */ }
    }

    private fun refreshBalanceBestEffort() {
        try {
            _balanceAtomic.value = handle.confirmedBalanceAtomic()
        } catch (_: DarkfiWalletNativeException.WalletNotInitialized) {
            _balanceAtomic.value = 0L
        } catch (e: DarkfiWalletNativeException) {
            _balanceAtomic.value = 0L
            _walletErrors.value = DarkfiWalletError.Setup(e)
        }
    }

    private fun applySyncSnapshotBestEffort() {
        runCatching { handle.syncSnapshot() }
            .onSuccess { applySyncSnapshot(it) }
    }

    private fun applySyncSnapshot(snapshot: DrkSyncSnapshot) {
        _processor.value = DarkfiSyncSnapshotMapping.processorInfo(snapshot)
        _progress.value = DarkfiSyncSnapshotMapping.progress(snapshot)
    }

    /** Address-only fallback when FFI deposit address is not ready yet. */
    private object NoOpDarkfidRpc : com.nighthawkapps.lib.android.sdk.wallet.rpc.DarkfidJsonRpcCaller {
        override suspend fun invoke(requestPayload: org.json.JSONObject): org.json.JSONObject = error("not used")
    }
}
