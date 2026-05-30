package com.nighthawkapps.lib.android.ui.screen.home.model

import com.nighthawkapps.lib.android.sdk.wallet.DarkfiPercent
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiProcessorInfo
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiSyncStatus
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiWalletError

data class WalletSnapshot(
    val status: DarkfiSyncStatus,
    val processorInfo: DarkfiProcessorInfo,
    val confirmedBalanceAtomic: Long,
    val progress: DarkfiPercent,
    val walletError: DarkfiWalletError?,
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
