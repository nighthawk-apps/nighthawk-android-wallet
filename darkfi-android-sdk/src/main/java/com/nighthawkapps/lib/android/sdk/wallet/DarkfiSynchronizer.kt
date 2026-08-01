package com.nighthawkapps.lib.android.sdk.wallet

import com.nighthawkapps.lib.android.sdk.dao.DarkfiDaoProposalDetail
import com.nighthawkapps.lib.android.sdk.dao.DarkfiDaoProposalSummary
import com.nighthawkapps.lib.android.sdk.dao.DarkfiDaoSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Wallet-facing sync API. Balance is a single atomic DRK value from the DarkFi wallet layer
 * (no transparent vs shielded split—all consensus transfers are private).
 *
 * **Chain / tx submission** is expected to flow through **darkfid** JSON-RPC (`DarkfidJsonRpc`) or
 * JNI from `drk`; this interface stays transport-agnostic until the native layer lands.
 */
interface DarkfiSynchronizer {
    val status: Flow<DarkfiSyncStatus>
    val processorInfo: Flow<DarkfiProcessorInfo>
    val progress: Flow<DarkfiPercent>

    /** Current sync method — OMR, trial decryption, fallback, etc. */
    val syncType: Flow<DarkfiSyncType>
        get() = flowOf(DarkfiSyncType.IDLE)

    /** Canonical live retrieval method (PerfOMR / LWEmongrass / BFV / trial). */
    val syncMethod: Flow<DarkfiSyncMethod>
        get() = flowOf(DarkfiSyncMethod.UNKNOWN)

    /** Human-readable sync status message for the home screen. */
    val syncStatusMessage: Flow<String>
        get() = flowOf("")

    /** Human-readable sync type label for the home screen. */
    val syncTypeMessage: Flow<String>
        get() = flowOf("")

    /** Whether OMR is available on the connected lightwallet server. */
    val omrAvailable: Flow<Boolean>
        get() = flowOf(false)

    /** Reason for fallback if syncMethod is TrialDecrypt when OMR was expected. */
    val fallbackReason: Flow<String>
        get() = flowOf("")

    /** User-facing message explaining the fallback. Empty when no fallback. */
    val fallbackUserMessage: Flow<String>
        get() = flowOf("")

    /** Confirmed spendable balance in smallest DRK units (wallet RPC / UniFFI mapping). */
    val confirmedBalanceAtomic: Flow<Long>

    /** Per-token balances (Money contract); empty when the native layer does not expose them. */
    val tokenBalances: Flow<List<DarkfiTokenBalance>>
        get() = flowOf(emptyList())

    val transactions: Flow<List<DarkfiTransactionOverview>>
    val walletErrors: Flow<DarkfiWalletError?>

    suspend fun getRecipients(tx: DarkfiTransactionOverview): List<DarkfiTransactionRecipient>

    fun walletAddresses(): DarkfiWalletAddresses

    fun generateNewAddress(): String

    suspend fun refreshNow()

    /** Updates wallet status from darkfid RPC probe (upstream `connect` signal parity). */
    fun applyDarkfidReachability(reachable: Boolean) {}

    /** True when this synchronizer can build and broadcast transfers via on-device `drk`. */
    val supportsNativeTransfer: Boolean

    /** Estimates network fee (atomic DRK) via `darkfid` `tx.calculate_fee` on a built transfer tx. */
    suspend fun estimateTransferFee(
        recipientAddress: String,
        amountDisplay: String,
        tokenId: String? = null,
        paymentMemo: String? = null,
    ): DarkfiTransferResult<Long>

    /** Builds, signs, and broadcasts a transfer; returns the transaction id on success. */
    suspend fun submitTransfer(
        recipientAddress: String,
        amountDisplay: String,
        tokenId: String? = null,
        paymentMemo: String? = null,
    ): DarkfiTransferResult<String>

    /**
     * Decrypts or loads the private payment memo for [txId] (outgoing memos are stored locally;
     * incoming memos are decrypted from Money transfer outputs).
     */
    suspend fun getTransactionPaymentMemo(txId: String): String?

    /** Imported DAOs from wallet DB (`drk dao list`). Empty when native FFI is unavailable. */
    suspend fun listDaos(): List<DarkfiDaoSummary> = emptyList()

    /** Proposals for one DAO, or all when [daoName] is null. */
    suspend fun listProposals(daoName: String? = null): List<DarkfiDaoProposalSummary> = emptyList()

    suspend fun getProposal(proposalBullaB58: String): DarkfiDaoProposalDetail? = null

    /** Build + broadcast a DAO transfer proposal (ZK proofs generated on-device). Returns proposal bulla b58. */
    suspend fun daoProposeTransfer(
        daoName: String,
        durationBlockwindows: Long,
        amount: String,
        tokenId: String? = null,
        recipientAddress: String,
    ): DarkfiTransferResult<String> = DarkfiTransferResult.Failure("DAO propose not supported")

    /** Vote yes/no on a DAO proposal (full governance token weight). Returns tx hash. */
    suspend fun daoVote(
        proposalBullaB58: String,
        voteYes: Boolean,
    ): DarkfiTransferResult<String> = DarkfiTransferResult.Failure("DAO vote not supported")
}

suspend fun DarkfiSynchronizer.validateAddressStub(address: String): DarkfiAddressType =
    when {
        address.startsWith("drk", ignoreCase = true) -> DarkfiAddressType.ConfidentialFormat
        address.length >= 32 -> DarkfiAddressType.PublicReceiveFormat
        else -> DarkfiAddressType.Invalid
    }

enum class DarkfiAddressType {
    /** Typical confidential / deposit-style encoding (e.g. `drk…`). */
    ConfidentialFormat,

    /** Shorter public-receive encoding; transfers still settle privately on DarkFi. */
    PublicReceiveFormat,
    Invalid,
}
