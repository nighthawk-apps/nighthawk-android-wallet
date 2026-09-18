package com.nighthawkapps.lib.android.sdk.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalNetworkPermissionTest {
    @Test
    fun requiredOnlyOnAndroid17() {
        assertFalse(LocalNetworkPermission.isRequired(36))
        assertTrue(LocalNetworkPermission.isRequired(37))
        assertTrue(LocalNetworkPermission.isRequired(38))
    }

    @Test
    fun runtimePermissionsEmptyBelow17() {
        assertTrue(LocalNetworkPermission.runtimePermissions(36).isEmpty())
    }

    @Test
    fun runtimePermissionsDeclareAccessLocalNetworkOn17() {
        val perms = LocalNetworkPermission.runtimePermissions(37)
        assertEquals(1, perms.size)
        assertEquals(LocalNetworkPermission.ACCESS_LOCAL_NETWORK, perms[0])
    }
}
