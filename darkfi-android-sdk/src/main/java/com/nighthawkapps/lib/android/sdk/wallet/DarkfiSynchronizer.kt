package com.nighthawkapps.lib.android.sdk.wallet

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

    /** Confirmed spendable balance in smallest DRK units (wallet RPC / UniFFI mapping). */
    val confirmedBalanceAtomic: Flow<Long>
    val transactions: Flow<List<DarkfiTransactionOverview>>
    val walletErrors: Flow<DarkfiWalletError?>

    suspend fun getRecipients(tx: DarkfiTransactionOverview): List<DarkfiTransactionRecipient>

    fun walletAddresses(): DarkfiWalletAddresses

    fun generateNewAddress(): String

    suspend fun refreshNow()

    /** True when this synchronizer can build and broadcast transfers via on-device `drk`. */
    val supportsNativeTransfer: Boolean

    /** Estimates network fee (atomic DRK) via `darkfid` `tx.calculate_fee` on a built transfer tx. */
    suspend fun estimateTransferFee(
        recipientAddress: String,
        amountDisplay: String,
        tokenId: String? = null,
    ): DarkfiTransferResult<Long>

    /** Builds, signs, and broadcasts a transfer; returns the transaction id on success. */
    suspend fun submitTransfer(
        recipientAddress: String,
        amountDisplay: String,
        tokenId: String? = null,
    ): DarkfiTransferResult<String>
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
