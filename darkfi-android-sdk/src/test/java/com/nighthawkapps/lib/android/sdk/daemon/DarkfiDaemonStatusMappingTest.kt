package com.nighthawkapps.lib.android.sdk.daemon

import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatConnectionState
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiSyncStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DarkfiDaemonStatusMappingTest {
    @Test
    fun connected_when_wallet_synced_and_chat_connected() {
        assertEquals(
            DarkfiDaemonStatus.Connected,
            DarkfiDaemonStatusMapping.map(
                walletPresent = true,
                walletStatus = DarkfiSyncStatus.SYNCED,
                walletHasError = false,
                chatState = DarkfiChatConnectionState.ConnectedViaTor,
                embeddedDarkircEnabled = true,
                bootstrapComplete = true,
            ),
        )
    }

    @Test
    fun connecting_when_wallet_syncing() {
        assertEquals(
            DarkfiDaemonStatus.Connecting,
            DarkfiDaemonStatusMapping.map(
                walletPresent = true,
                walletStatus = DarkfiSyncStatus.SYNCING,
                walletHasError = false,
                chatState = DarkfiChatConnectionState.Disconnected,
                embeddedDarkircEnabled = false,
                bootstrapComplete = true,
            ),
        )
    }

    @Test
    fun error_when_wallet_reports_error() {
        assertEquals(
            DarkfiDaemonStatus.Error,
            DarkfiDaemonStatusMapping.map(
                walletPresent = true,
                walletStatus = DarkfiSyncStatus.SYNCED,
                walletHasError = true,
                chatState = DarkfiChatConnectionState.ConnectedDirect,
                embeddedDarkircEnabled = false,
                bootstrapComplete = true,
            ),
        )
    }

    @Test
    fun starting_before_bootstrap_complete() {
        assertEquals(
            DarkfiDaemonStatus.Starting,
            DarkfiDaemonStatusMapping.map(
                walletPresent = true,
                walletStatus = DarkfiSyncStatus.SYNCED,
                walletHasError = false,
                chatState = DarkfiChatConnectionState.ConnectedDirect,
                embeddedDarkircEnabled = true,
                bootstrapComplete = false,
            ),
        )
    }

    @Test
    fun error_when_wallet_reports_proto_mismatch() {
        assertEquals(
            DarkfiDaemonStatus.Error,
            DarkfiDaemonStatusMapping.map(
                walletPresent = true,
                walletStatus = DarkfiSyncStatus.PROTO_MISMATCH,
                walletHasError = false,
                chatState = DarkfiChatConnectionState.ConnectedDirect,
                embeddedDarkircEnabled = false,
                bootstrapComplete = true,
            ),
        )
    }

    @Test
    fun reorgRecovery_when_wallet_reports_reorg_detected() {
        assertEquals(
            DarkfiDaemonStatus.ReorgRecovery,
            DarkfiDaemonStatusMapping.map(
                walletPresent = true,
                walletStatus = DarkfiSyncStatus.REORG_DETECTED,
                walletHasError = false,
                chatState = DarkfiChatConnectionState.ConnectedDirect,
                embeddedDarkircEnabled = false,
                bootstrapComplete = true,
            ),
        )
    }

    @Test
    fun shouldAutoRetryWalletSync_onlyWhenDisconnectedOrFailed() {
        assertTrue(DarkfiDaemonStatusMapping.shouldAutoRetryWalletSync(DarkfiSyncStatus.DISCONNECTED))
        assertTrue(DarkfiDaemonStatusMapping.shouldAutoRetryWalletSync(DarkfiSyncStatus.DEGRADED))
        assertTrue(DarkfiDaemonStatusMapping.shouldAutoRetryWalletSync(DarkfiSyncStatus.ERROR))
        assertTrue(DarkfiDaemonStatusMapping.shouldAutoRetryWalletSync(DarkfiSyncStatus.STOPPED))
        assertTrue(DarkfiDaemonStatusMapping.shouldAutoRetryWalletSync(DarkfiSyncStatus.RETRYING))

        assertFalse(DarkfiDaemonStatusMapping.shouldAutoRetryWalletSync(null))
        assertFalse(DarkfiDaemonStatusMapping.shouldAutoRetryWalletSync(DarkfiSyncStatus.SYNCED))
        assertFalse(DarkfiDaemonStatusMapping.shouldAutoRetryWalletSync(DarkfiSyncStatus.CONNECTING))
        assertFalse(DarkfiDaemonStatusMapping.shouldAutoRetryWalletSync(DarkfiSyncStatus.SYNCING))
        assertFalse(DarkfiDaemonStatusMapping.shouldAutoRetryWalletSync(DarkfiSyncStatus.SCANNING))
        assertFalse(DarkfiDaemonStatusMapping.shouldAutoRetryWalletSync(DarkfiSyncStatus.REORG_DETECTED))
        assertFalse(DarkfiDaemonStatusMapping.shouldAutoRetryWalletSync(DarkfiSyncStatus.PROTO_MISMATCH))
    }
}
