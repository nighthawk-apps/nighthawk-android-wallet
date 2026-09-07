package com.nighthawkapps.lib.android.sdk.daemon

import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatConnectionState
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiSyncStatus
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
