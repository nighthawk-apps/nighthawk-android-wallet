package com.nighthawkapps.lib.android.sdk.wallet

import android.content.Context
import com.nighthawkapps.lib.android.sdk.dao.toSdk
import com.nighthawkapps.lib.android.sdk.uniffi.DarkfiMobileFfiApi
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.DarkfiWalletHandle
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.DarkfiWalletNativeException
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.DrkSyncSnapshot
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.ReorgEvent
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.ReorgEventCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * [DarkfiSynchronizer] backed by UniFFI **`DarkfiWalletHandle`** (on-device **`drk`**).
 */
@Suppress("TooManyFunctions")
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
    private val _tokenBalances = MutableStateFlow<List<DarkfiTokenBalance>>(emptyList())
    private val _transactions = MutableStateFlow<List<DarkfiTransactionOverview>>(emptyList())
    private val _walletErrors = MutableStateFlow<DarkfiWalletError?>(null)
    private val _syncType = MutableStateFlow(DarkfiSyncType.IDLE)
    private val _syncMethod = MutableStateFlow(DarkfiSyncMethod.UNKNOWN)
    private val _syncStatusMessage = MutableStateFlow("")
    private val _syncTypeMessage = MutableStateFlow("")
    private val _omrAvailable = MutableStateFlow(false)
    private val _fallbackReason = MutableStateFlow("")
    private val _fallbackUserMessage = MutableStateFlow("")
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncJob: Job? = null

    init {
        handle.setReorgCallback(
            object : ReorgEventCallback {
                override fun onReorg(event: ReorgEvent) {
                    _fallbackReason.value = "Reorg"
                    _fallbackUserMessage.value = event.summaryMessage
                    _syncStatusMessage.value = event.summaryMessage
                    refreshBalanceBestEffort()
                    refreshTransactionsBestEffort()
                    applySyncSnapshotBestEffort()
                }
            },
        )
        reportEndpointNetworkMismatchIfNeeded()
        refreshBalanceBestEffort()
        refreshTokenBalancesBestEffort()
        refreshTransactionsBestEffort()
        applySyncSnapshotBestEffort()
        startSyncProgressPolling()
        _status.value = DarkfiSyncStatus.DISCONNECTED
    }

    private fun reportEndpointNetworkMismatchIfNeeded() {
        when (val check = DarkfiEndpointNetworkGuard.validate(wallet.network, wallet.endpoint)) {
            is DarkfiEndpointNetworkGuard.Result.Mismatch -> {
                _walletErrors.value =
                    DarkfiWalletError.Processor(
                        IllegalStateException(DarkfiEndpointNetworkGuard.mismatchMessage(check)),
                    )
            }

            DarkfiEndpointNetworkGuard.Result.Ok -> {
                Unit
            }
        }
    }

    override fun applyDarkfidReachability(reachable: Boolean) {
        if (reachable) {
            _walletErrors.value = null
            // Don't prematurely set SYNCED — delegate to the snapshot so the
            // reported status reflects the actual sync state from the FFI layer.
            applySyncSnapshotBestEffort()
        } else {
            _status.value = DarkfiSyncStatus.DISCONNECTED
            _walletErrors.value =
                DarkfiWalletError.Processor(
                    IllegalStateException(
                        "lightwalletd unreachable at ${wallet.endpoint.toDisplayString()}",
                    ),
                )
        }
    }

    override val status: Flow<DarkfiSyncStatus> = _status.asStateFlow()
    override val processorInfo: Flow<DarkfiProcessorInfo> = _processor.asStateFlow()
    override val progress: Flow<DarkfiPercent> = _progress.asStateFlow()
    override val confirmedBalanceAtomic: Flow<Long> = _balanceAtomic.asStateFlow()
    override val tokenBalances: Flow<List<DarkfiTokenBalance>> = _tokenBalances.asStateFlow()
    override val transactions: Flow<List<DarkfiTransactionOverview>> = _transactions.asStateFlow()
    override val walletErrors: Flow<DarkfiWalletError?> = _walletErrors.asStateFlow()
    override val syncType: Flow<DarkfiSyncType> = _syncType.asStateFlow()
    override val syncMethod: Flow<DarkfiSyncMethod> = _syncMethod.asStateFlow()
    override val syncStatusMessage: Flow<String> = _syncStatusMessage.asStateFlow()
    override val syncTypeMessage: Flow<String> = _syncTypeMessage.asStateFlow()
    override val omrAvailable: Flow<Boolean> = _omrAvailable.asStateFlow()
    override val fallbackReason: Flow<String> = _fallbackReason.asStateFlow()
    override val fallbackUserMessage: Flow<String> = _fallbackUserMessage.asStateFlow()

    override val supportsNativeTransfer: Boolean = true

    override suspend fun getRecipients(tx: DarkfiTransactionOverview): List<DarkfiTransactionRecipient> =
        withContext(Dispatchers.IO) {
            val address =
                tx.recipientAddress?.takeIf { it.isNotBlank() }
                    ?: runCatching { handle.transactionRecipient(tx.rawId) }
                        .getOrNull()
                        ?.takeIf { it.isNotBlank() }
            address?.let { listOf(DarkfiTransactionRecipient(it)) }.orEmpty()
        }

    override suspend fun estimateTransferFee(
        recipientAddress: String,
        amountDisplay: String,
        tokenId: String?,
        paymentMemo: String?,
    ): DarkfiTransferResult<Long> =
        withContext(Dispatchers.IO) {
            when (val memo = validatePaymentMemo(paymentMemo)) {
                is DarkfiTransferResult.Failure -> {
                    memo
                }

                is DarkfiTransferResult.Success -> {
                    estimateTransferFeeAfterMemoValidated(
                        recipientAddress,
                        amountDisplay,
                        tokenId,
                        memo.value,
                    )
                }
            }
        }

    private fun estimateTransferFeeAfterMemoValidated(
        recipientAddress: String,
        amountDisplay: String,
        tokenId: String?,
        paymentMemo: String?,
    ): DarkfiTransferResult<Long> =
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
                                paymentMemo,
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

    override suspend fun submitTransfer(
        recipientAddress: String,
        amountDisplay: String,
        tokenId: String?,
        paymentMemo: String?,
    ): DarkfiTransferResult<String> =
        withContext(Dispatchers.IO) {
            when (val memo = validatePaymentMemo(paymentMemo)) {
                is DarkfiTransferResult.Failure -> {
                    memo
                }

                is DarkfiTransferResult.Success -> {
                    submitTransferAfterMemoValidated(
                        recipientAddress,
                        amountDisplay,
                        tokenId,
                        memo.value,
                    )
                }
            }
        }

    private fun submitTransferAfterMemoValidated(
        recipientAddress: String,
        amountDisplay: String,
        tokenId: String?,
        paymentMemo: String?,
    ): DarkfiTransferResult<String> =
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
                                    paymentMemo,
                                )
                            handle.broadcastTransfer(txBytes, paymentMemo, recipient.value)
                        }.fold(
                            onSuccess = { txid ->
                                refreshBalanceBestEffort()
                                refreshTokenBalancesBestEffort()
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

    override suspend fun getTransactionPaymentMemo(txId: String): String? =
        withContext(Dispatchers.IO) {
            runCatching { handle.transactionPaymentMemo(txId.trim()) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
        }

    private fun validatePaymentMemo(paymentMemo: String?): DarkfiTransferResult<String?> =
        runCatching { DarkfiPaymentMemo.normalize(paymentMemo) }
            .fold(
                onSuccess = { DarkfiTransferResult.Success(it) },
                onFailure = { e ->
                    DarkfiTransferResult.Failure(e.message ?: "Invalid memo", e)
                },
            )

    override fun walletAddresses(): DarkfiWalletAddresses =
        runCatching { handle.listAddresses() }
            .map { addrs ->
                DarkfiWalletAddresses(
                    privateAddresses = addrs,
                )
            }.recoverCatching {
                // listAddresses may fail if node is unreachable; try local-only primary address
                val primary = handle.primaryDepositAddress()
                DarkfiWalletAddresses(privateAddresses = listOf(primary))
            }.getOrElse {
                StubDarkfiSynchronizer(wallet, NoOpDarkfidRpc).displayAddresses()
            }

    override fun generateNewAddress(): String = handle.generateNewAddress()

    override fun setStrictOmrOnly(strict: Boolean) {
        handle.setStrictOmrOnly(strict)
    }

    override suspend fun refreshNow() {
        _status.value = DarkfiSyncStatus.SYNCING
        runCatching { handle.refreshNow() }
            .onSuccess {
                applySyncSnapshot(it)
                _walletErrors.value = null
            }.onFailure { e ->
                android.util.Log.e("NativeDarkfiSynchronizer", "refreshNow failed", e)
                _walletErrors.value =
                    DarkfiWalletError.Processor(e)
                _status.value = DarkfiSyncStatus.DISCONNECTED
            }
        refreshBalanceBestEffort()
        refreshTokenBalancesBestEffort()
        refreshTransactionsBestEffort()
        _status.value =
            if (_walletErrors.value != null) {
                DarkfiSyncStatus.DISCONNECTED
            } else {
                DarkfiSyncStatus.SYNCED
            }
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
        }
    }

    private fun refreshTokenBalancesBestEffort() {
        runCatching { handle.listTokenBalances() }
            .onSuccess { _tokenBalances.value = DrkTokenBalanceMapping.balances(it) }
            .onFailure { /* keep last known list */ }
    }

    private fun startSyncProgressPolling() {
        syncJob = syncScope.launch {
            while (isActive) {
                applySyncSnapshotBestEffort()
                delay(SYNC_PROGRESS_POLL_MS)
            }
        }
    }

    override fun close() {
        syncJob?.cancel()
        syncScope.cancel()
        handle.close()
    }

    private companion object {
        const val SYNC_PROGRESS_POLL_MS = 15_000L
    }

    private fun applySyncSnapshotBestEffort() {
        runCatching { handle.syncSnapshot() }
            .onSuccess { applySyncSnapshot(it) }

        // Also read the light sync state for OMR/sync type info
        runCatching { handle.lightSyncSnapshot() }
            .onSuccess { lightState ->
                _syncType.value = DarkfiSyncType.fromRustString(lightState.syncType)
                _syncMethod.value = DarkfiSyncMethod.fromRust(lightState.syncMethod)
                _syncStatusMessage.value = lightState.statusMessage ?: ""
                _syncTypeMessage.value = lightState.syncTypeMessage ?: ""
                _omrAvailable.value = lightState.omrAvailable
                _fallbackReason.value = lightState.fallbackReason.name
                _fallbackUserMessage.value = lightState.fallbackUserMessage

                // Map light sync status to DarkfiSyncStatus.
                // Uses lightState.status (LightSyncStatus enum Display output:
                // "Disconnected", "Connecting", "Syncing", etc.) — NOT
                // lightState.statusMessage which contains free-text like
                // "Syncing block 42 of 1000" that would never match.
                _status.value =
                    if (lightState.protoVersionMismatch) {
                        DarkfiSyncStatus.PROTO_MISMATCH
                    } else {
                        when (lightState.status) {
                            "Disconnected" -> DarkfiSyncStatus.DISCONNECTED
                            "Connecting" -> DarkfiSyncStatus.CONNECTING
                            "Syncing" -> DarkfiSyncStatus.SYNCING
                            "Synced" -> DarkfiSyncStatus.SYNCED
                            "Retrying" -> DarkfiSyncStatus.RETRYING
                            "Degraded" -> DarkfiSyncStatus.DEGRADED
                            "Error" -> DarkfiSyncStatus.ERROR
                            else -> _status.value
                        }
                    }
            }
    }

    private fun applySyncSnapshot(snapshot: DrkSyncSnapshot) {
        _processor.value = DarkfiSyncSnapshotMapping.processorInfo(snapshot)
        _progress.value = DarkfiSyncSnapshotMapping.progress(snapshot)
    }

    override suspend fun listDaos() =
        withContext(Dispatchers.IO) {
            handle.listDaos().map { it.toSdk() }
        }

    override suspend fun listProposals(daoName: String?) =
        withContext(Dispatchers.IO) {
            handle.listProposals(daoName).map { it.toSdk() }
        }

    override suspend fun getProposal(proposalBullaB58: String) =
        withContext(Dispatchers.IO) {
            runCatching { handle.getProposal(proposalBullaB58.trim()).toSdk() }.getOrNull()
        }

    override suspend fun daoProposeTransfer(
        daoName: String,
        durationBlockwindows: Long,
        amount: String,
        tokenId: String?,
        recipientAddress: String,
    ): DarkfiTransferResult<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                handle.daoProposeTransfer(
                    daoName.trim(),
                    durationBlockwindows.toULong(),
                    amount.trim(),
                    tokenId?.trim()?.takeIf { it.isNotEmpty() },
                    recipientAddress.trim(),
                )
            }.fold(
                onSuccess = { DarkfiTransferResult.Success(it) },
                onFailure = { e ->
                    DarkfiTransferResult.Failure(
                        e.message ?: "DAO propose failed",
                        e,
                    )
                },
            )
        }

    override suspend fun daoVote(
        proposalBullaB58: String,
        voteYes: Boolean,
    ): DarkfiTransferResult<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                handle.daoVote(proposalBullaB58.trim(), voteYes)
            }.fold(
                onSuccess = { DarkfiTransferResult.Success(it) },
                onFailure = { e ->
                    DarkfiTransferResult.Failure(
                        e.message ?: "DAO vote failed",
                        e,
                    )
                },
            )
        }

    /** Address-only fallback when FFI deposit address is not ready yet. */
    private object NoOpDarkfidRpc : com.nighthawkapps.lib.android.sdk.wallet.rpc.DarkfidJsonRpcCaller {
        override suspend fun invoke(requestPayload: org.json.JSONObject): org.json.JSONObject = error("not used")
    }
}
