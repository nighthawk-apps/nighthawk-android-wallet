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
        assertFalse(isSendDisabled(DarkfiSyncStatus.SYNCED))
        assertFalse(isSendDisabled(DarkfiSyncStatus.SYNCING))
        assertFalse(isSendDisabled(DarkfiSyncStatus.CONNECTING))
        assertFalse(isSendDisabled(DarkfiSyncStatus.DISCONNECTED))
    }
}
