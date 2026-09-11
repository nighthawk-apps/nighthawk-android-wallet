package com.nighthawkapps.lib.android.sdk.wallet

import android.app.Application
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DarkfiWalletCoordinatorTest {
    private val context: Application
        get() = RuntimeEnvironment.getApplication() as Application

    private val wallet =
        PersistableDarkfiWallet(
            seedPhrase = List(22) { "abandon" },
            network = DarkfiNetwork.Testnet,
            endpoint = DarkfiEndpoint.defaultForNetwork(DarkfiNetwork.Testnet),
            birthdayHeight = null,
        )

    @Test
    fun nullWallet_keepsSynchronizerNull() =
        runTest {
            val flow = MutableStateFlow<PersistableDarkfiWallet?>(null)
            val coordinator = DarkfiWalletCoordinator(context, flow, useNativeSynchronizer = false)
            testScheduler.advanceUntilIdle()
            assertNull(coordinator.synchronizer.value)
        }

    @Test
    fun walletFlow_emitsStubSynchronizer() =
        runTest {
            val flow = MutableStateFlow<PersistableDarkfiWallet?>(null)
            val coordinator = DarkfiWalletCoordinator(context, flow, useNativeSynchronizer = false)
            flow.value = wallet
            // Coordinator opens the stub on Dispatchers.IO; virtual delay() does
            // not wait for that work. Same real-time poll as the other tests.
            awaitStub(coordinator)
        }

    @Test
    fun backupNotComplete_doesNotOpenSynchronizer() =
        runTest {
            val walletFlow = MutableStateFlow<PersistableDarkfiWallet?>(wallet)
            val allowed = MutableStateFlow(false)
            val coordinator =
                DarkfiWalletCoordinator(
                    context,
                    walletFlow,
                    useNativeSynchronizer = false,
                    synchronizerAllowed = allowed,
                )
            testScheduler.advanceUntilIdle()
            awaitNull(coordinator)
            assertNull(coordinator.synchronizer.value)
            allowed.value = true
            awaitStub(coordinator)
        }

    @Test
    fun resetSdk_clearsSynchronizer() =
        runTest {
            val flow = MutableStateFlow(wallet)
            val coordinator = DarkfiWalletCoordinator(context, flow, useNativeSynchronizer = false)
            val sync = awaitStub(coordinator)
            coordinator.resetSdk()
            awaitNull(coordinator)
            assertNull(coordinator.synchronizer.value)
            assertTrue(sync.closeCount >= 1)
        }

    @Test
    fun walletSwitch_closesPreviousSynchronizer() =
        runTest {
            val flow = MutableStateFlow<PersistableDarkfiWallet?>(wallet)
            val coordinator = DarkfiWalletCoordinator(context, flow, useNativeSynchronizer = false)
            val first = awaitStub(coordinator)
            flow.value = null
            awaitNull(coordinator)
            assertNull(coordinator.synchronizer.value)
            assertTrue(first.closeCount >= 1)
        }

    private fun awaitStub(coordinator: DarkfiWalletCoordinator): StubDarkfiSynchronizer {
        repeat(50) {
            (coordinator.synchronizer.value as? StubDarkfiSynchronizer)?.let { return it }
            Thread.sleep(20)
        }
        val value = coordinator.synchronizer.value
        assertTrue("expected stub synchronizer, got $value", value is StubDarkfiSynchronizer)
        return value as StubDarkfiSynchronizer
    }

    private fun awaitNull(coordinator: DarkfiWalletCoordinator) {
        repeat(50) {
            if (coordinator.synchronizer.value == null) return
            Thread.sleep(20)
        }
        assertNull(coordinator.synchronizer.value)
    }
}
