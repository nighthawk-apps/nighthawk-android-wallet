package com.nighthawkapps.lib.android.sdk.mesh

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
class MeshCoordinatorTest {
    @Test
    fun fgsStartRefusesApplicationContext() {
        val app: Application = ApplicationProvider.getApplicationContext()
        assertFalse(NighthawkMeshService.startFromForeground(app))
        assertFalse(MeshPowerPolicy.mayStartForegroundServiceFromBackground())
    }

    @Test
    fun gatewayOptInStaysOffThisPass() {
        MeshCoordinator.setGatewayOptIn(true)
        assertFalse(MeshCoordinator.gatewayOptIn)
        MeshCoordinator.setGatewayOptIn(false)
        assertFalse(MeshCoordinator.gatewayOptIn)
    }

    @Test
    fun gatewayAnnounceIsRemembered() {
        val id = ByteArray(8) { 7 }
        MeshCoordinator.noteGatewayAnnounce(id, 0x03)
        assert(MeshCoordinator.lastGatewayCaps == 0x03)
        assert(MeshCoordinator.lastGatewayId.contentEquals(id))
        MeshCoordinator.clearGatewayHint()
        assert(MeshCoordinator.lastGatewayId == null)
    }
}
