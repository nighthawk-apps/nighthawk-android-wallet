package com.nighthawkapps.lib.android.ui.fixture

import com.nighthawkapps.lib.android.sdk.wallet.DarkfiPercent
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiProcessorInfo
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiSyncStatus
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiWalletError
import com.nighthawkapps.lib.android.ui.screen.home.model.WalletSnapshot

@Suppress("MagicNumber")
object WalletSnapshotFixture {
    val STATUS = DarkfiSyncStatus.SYNCED
    val PROGRESS = DarkfiPercent.ZERO_PERCENT

    @Suppress("LongParameterList")
    fun new(
        status: DarkfiSyncStatus = STATUS,
        processorInfo: DarkfiProcessorInfo =
            DarkfiProcessorInfo(
                scannedBlocks = 1L,
                chainTip = 1L,
            ),
        confirmedBalanceAtomic: Long = 0L,
        progress: DarkfiPercent = PROGRESS,
        walletError: DarkfiWalletError? = null,
    ) = WalletSnapshot(
        status = status,
        processorInfo = processorInfo,
        confirmedBalanceAtomic = confirmedBalanceAtomic,
        progress = progress,
        walletError = walletError,
    )
}
