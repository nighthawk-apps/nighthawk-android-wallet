package com.nighthawkapps.lib.android.ui.screen.home.model

import com.nighthawkapps.lib.android.sdk.wallet.DarkfiPercent
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiProcessorInfo
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiSyncMethod
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiSyncStatus
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiSyncType
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiWalletError

data class WalletSnapshot(
    val status: DarkfiSyncStatus,
    val processorInfo: DarkfiProcessorInfo,
    val confirmedBalanceAtomic: Long,
    val progress: DarkfiPercent,
    val walletError: DarkfiWalletError?,
    /** Current sync state phase — OMR, trial decryption, fallback, etc. */
    val syncType: DarkfiSyncType = DarkfiSyncType.IDLE,
    /** Canonical retrieval/encryption method live on the wire (PerfOMR / LWEmongrass / …). */
    val syncMethod: DarkfiSyncMethod = DarkfiSyncMethod.UNKNOWN,
    /** Human-readable sync status for the home screen (e.g. "Syncing block 42 of 1000"). */
    val syncStatusMessage: String = "",
    /** Human-readable sync type label for the home screen (e.g. "OMR", "Trial decryption fallback"). */
    val syncTypeMessage: String = "",
    /** Whether OMR is available on the connected lightwallet server. */
    val omrAvailable: Boolean = false,
    /** Reason for fallback if syncMethod is TrialDecrypt when OMR was expected. */
    val fallbackReason: String = "",
    /** User-facing message explaining the fallback. Empty when no fallback. */
    val fallbackUserMessage: String = "",
) {
    val isSendEnabled: Boolean get() = confirmedBalanceAtomic > 0L
}

fun WalletSnapshot.canSpend(amountAtomic: Long): Boolean = spendableBalanceAtomic() >= amountAtomic

fun WalletSnapshot.totalBalanceAtomic(): Long = confirmedBalanceAtomic

fun WalletSnapshot.spendableBalanceAtomic(): Long = confirmedBalanceAtomic

fun WalletSnapshot.pendingBalanceAtomic(): Long = 0L

fun WalletSnapshot.hasPendingBalance(): Boolean = pendingBalanceAtomic() > 0L

@Suppress("unused")
fun WalletSnapshot.changePendingBalance(): Long = 0L

@Suppress("unused")
fun WalletSnapshot.valuePendingBalance(): Long = 0L

fun WalletSnapshot.hasChangePending(): Boolean = false

fun WalletSnapshot.hasValuePending(): Boolean = false
