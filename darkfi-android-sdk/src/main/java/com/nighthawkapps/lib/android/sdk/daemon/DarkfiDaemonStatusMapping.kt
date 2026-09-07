@file:Suppress("LongParameterList", "CyclomaticComplexMethod", "ReturnCount")

package com.nighthawkapps.lib.android.sdk.daemon

import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatConnectionState
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiSyncStatus

/** Maps wallet + chat subsystem signals into [DarkfiDaemonStatus] (pure, testable). */
object DarkfiDaemonStatusMapping {
    fun map(
        walletPresent: Boolean,
        walletStatus: DarkfiSyncStatus?,
        walletHasError: Boolean,
        chatState: DarkfiChatConnectionState,
        embeddedDarkircEnabled: Boolean,
        bootstrapComplete: Boolean,
    ): DarkfiDaemonStatus {
        if (!bootstrapComplete) {
            return DarkfiDaemonStatus.Starting
        }
        if (!walletPresent) {
            return DarkfiDaemonStatus.Stopped
        }
        if (walletHasError || chatState == DarkfiChatConnectionState.Error) {
            return DarkfiDaemonStatus.Error
        }
        when (walletStatus) {
            DarkfiSyncStatus.DISCONNECTED,
            DarkfiSyncStatus.RETRYING,
            DarkfiSyncStatus.DEGRADED -> return DarkfiDaemonStatus.Reconnecting

            DarkfiSyncStatus.SYNCING,
            DarkfiSyncStatus.CONNECTING -> return DarkfiDaemonStatus.Connecting

            DarkfiSyncStatus.STOPPED -> return DarkfiDaemonStatus.Stopped

            DarkfiSyncStatus.ERROR,
            DarkfiSyncStatus.PROTO_MISMATCH -> return DarkfiDaemonStatus.Error

            DarkfiSyncStatus.REORG_DETECTED -> return DarkfiDaemonStatus.ReorgRecovery

            DarkfiSyncStatus.SYNCED -> Unit

            null -> return DarkfiDaemonStatus.Unknown
        }
        if (embeddedDarkircEnabled) {
            return when (chatState) {
                DarkfiChatConnectionState.Connecting -> DarkfiDaemonStatus.Connecting

                DarkfiChatConnectionState.Degraded -> DarkfiDaemonStatus.Reconnecting

                DarkfiChatConnectionState.Disconnected -> DarkfiDaemonStatus.Reconnecting

                DarkfiChatConnectionState.ConnectedDirect,
                DarkfiChatConnectionState.ConnectedViaTor,
                -> DarkfiDaemonStatus.Connected

                DarkfiChatConnectionState.Error -> DarkfiDaemonStatus.Error
            }
        }
        return DarkfiDaemonStatus.Connected
    }
}
