package com.nighthawkapps.lib.android.ui.screen.wallet.view

import com.nighthawkapps.lib.android.sdk.wallet.DarkfiSyncStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletSendDisabledTest {
    @Test
    fun send_isDisabledOnlyWhenSyncFailed() {
        assertTrue(isSendDisabled(DarkfiSyncStatus.ERROR))
        assertTrue(isSendDisabled(DarkfiSyncStatus.STOPPED))
        assertTrue(isSendDisabled(DarkfiSyncStatus.PROTO_MISMATCH))
        assertTrue(isSendDisabled(DarkfiSyncStatus.SCANNING))
        assertFalse(isSendDisabled(DarkfiSyncStatus.SYNCED))
        assertFalse(isSendDisabled(DarkfiSyncStatus.SYNCING))
        assertFalse(isSendDisabled(DarkfiSyncStatus.CONNECTING))
        assertFalse(isSendDisabled(DarkfiSyncStatus.DISCONNECTED))
    }

    @Test
    fun flowSyncWave_shownOnlyWhileActivelyConnectingOrSyncing() {
        assertTrue(shouldShowFlowSyncWave(DarkfiSyncStatus.CONNECTING))
        assertTrue(shouldShowFlowSyncWave(DarkfiSyncStatus.SYNCING))
        assertTrue(shouldShowFlowSyncWave(DarkfiSyncStatus.SCANNING))
        assertTrue(shouldShowFlowSyncWave(DarkfiSyncStatus.RETRYING))
        assertTrue(shouldShowFlowSyncWave(DarkfiSyncStatus.REORG_DETECTED))

        assertFalse(shouldShowFlowSyncWave(DarkfiSyncStatus.DISCONNECTED))
        assertFalse(shouldShowFlowSyncWave(DarkfiSyncStatus.ERROR))
        assertFalse(shouldShowFlowSyncWave(DarkfiSyncStatus.STOPPED))
        assertFalse(shouldShowFlowSyncWave(DarkfiSyncStatus.DEGRADED))
        assertFalse(shouldShowFlowSyncWave(DarkfiSyncStatus.PROTO_MISMATCH))
        assertFalse(shouldShowFlowSyncWave(DarkfiSyncStatus.SYNCED))
    }
}
