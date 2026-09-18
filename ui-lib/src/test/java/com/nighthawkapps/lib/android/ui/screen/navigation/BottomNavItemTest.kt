package com.nighthawkapps.lib.android.ui.screen.navigation

import com.nighthawkapps.lib.android.ui.screen.navigation.NavigationTargets.RECEIVE_MONEY
import com.nighthawkapps.lib.android.ui.screen.navigation.NavigationTargets.REQUEST_MONEY
import com.nighthawkapps.lib.android.ui.screen.navigation.NavigationTargets.SCAN
import com.nighthawkapps.lib.android.ui.screen.navigation.NavigationTargets.SEND_MONEY
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomNavItemTest {
    @Test
    fun wallet_isSelectedForSendReceiveRequestAndScan() {
        assertTrue(isBottomNavItemSelected(BottomNavItem.Wallet.route, BottomNavItem.Wallet.route))
        assertTrue(isBottomNavItemSelected(BottomNavItem.Wallet.route, SEND_MONEY))
        assertTrue(isBottomNavItemSelected(BottomNavItem.Wallet.route, RECEIVE_MONEY))
        assertTrue(isBottomNavItemSelected(BottomNavItem.Wallet.route, REQUEST_MONEY))
        assertTrue(isBottomNavItemSelected(BottomNavItem.Wallet.route, SCAN))
        assertFalse(isBottomNavItemSelected(BottomNavItem.Wallet.route, BottomNavItem.Dex.route))
    }

    @Test
    fun dex_isItsOwnTab() {
        assertTrue(isBottomNavItemSelected(BottomNavItem.Dex.route, BottomNavItem.Dex.route))
        assertFalse(isBottomNavItemSelected(BottomNavItem.Dex.route, SEND_MONEY))
        assertFalse(isBottomNavItemSelected(BottomNavItem.Dex.route, BottomNavItem.Wallet.route))
    }
}
