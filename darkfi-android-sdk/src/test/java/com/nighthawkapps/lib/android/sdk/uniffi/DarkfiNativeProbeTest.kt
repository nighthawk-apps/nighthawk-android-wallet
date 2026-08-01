package com.nighthawkapps.lib.android.sdk.uniffi

import android.app.Application
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiEndpoint
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiNetwork
import com.nighthawkapps.lib.android.sdk.wallet.DrkWalletPaths
import com.nighthawkapps.lib.android.sdk.wallet.PersistableDarkfiWallet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DarkfiNativeProbeTest {
    @Test
    fun run_doesNotThrow() {
        when (val probe = DarkfiNativeProbe.run()) {
            is DarkfiNativeProbe.Ok -> Unit
            DarkfiNativeProbe.MissingLibrary -> Unit
            is DarkfiNativeProbe.Broken -> Unit
        }
    }

    @Test
    fun run_reportsMissingOrOk() {
        val probe = DarkfiNativeProbe.run()
        assertTrue(
            probe is DarkfiNativeProbe.Ok ||
                probe is DarkfiNativeProbe.MissingLibrary ||
                probe is DarkfiNativeProbe.Broken,
        )
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DarkfiMobileFfiBootstrapConfigTest {
    private val context: Application
        get() = RuntimeEnvironment.getApplication() as Application

    @org.junit.Before
    fun setUp() {
        val testFile = java.io.File(context.filesDir, "test_pass.preferences_pb")
        val prefs = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
            produceFile = { testFile }
        )
        val field = com.nighthawkapps.lib.android.sdk.wallet.DrkWalletPassStore::class.java.getDeclaredField("dataStoreInstance")
        field.isAccessible = true
        field.set(null, prefs)
    }

    @org.junit.After
    fun tearDown() {
        val field = com.nighthawkapps.lib.android.sdk.wallet.DrkWalletPassStore::class.java.getDeclaredField("dataStoreInstance")
        field.isAccessible = true
        field.set(null, null)
    }

    private val wallet =
        PersistableDarkfiWallet(
            seedPhrase = List(22) { "word$it" },
            network = DarkfiNetwork.Testnet,
            endpoint = DarkfiEndpoint.defaultForNetwork(DarkfiNetwork.Testnet),
            birthdayHeight = 100L,
        )

    @Test
    fun buildBootstrapConfig_mapsFields() {
        val config = DarkfiMobileFfiApi.buildBootstrapConfig(context, wallet)
        assertEquals("testnet", config.network)
        assertEquals(22, config.mnemonic.size)
        assertEquals(wallet.endpoint.toDisplayString(), config.lightwalletServerUrl)
        assertEquals(100L, config.birthdayHeight)
        assertTrue(config.walletDbPath.contains("wallet.db"))
        assertTrue(config.cachePath.endsWith("cache"))
        assertTrue(config.walletPass.isNotEmpty())
    }

    @Test
    fun buildBootstrapConfig_nullBirthday_mapsToNegativeOne() {
        val config =
            DarkfiMobileFfiApi.buildBootstrapConfig(
                context,
                wallet.copy(birthdayHeight = null),
            )
        assertEquals(-1L, config.birthdayHeight)
    }

    @Test
    fun buildBootstrapConfig_zeroBirthday_keptForTipSeed() {
        val config =
            DarkfiMobileFfiApi.buildBootstrapConfig(
                context,
                wallet.copy(birthdayHeight = 0L),
            )
        // Fresh create: 0 is intentional (FFI seeds at LWD tip); must not coerce to -1.
        assertEquals(0L, config.birthdayHeight)
    }

    @Test
    fun buildBootstrapConfig_mainnetNetworkLabel() {
        val config =
            DarkfiMobileFfiApi.buildBootstrapConfig(
                context,
                wallet.copy(network = DarkfiNetwork.Mainnet),
            )
        assertEquals("mainnet", config.network)
    }

    @Test
    fun buildBootstrapConfig_createsDrkDirectories() {
        DarkfiMobileFfiApi.buildBootstrapConfig(context, wallet)
        assertTrue(DrkWalletPaths.root(context).isDirectory)
        assertTrue(DrkWalletPaths.cacheDir(context).isDirectory)
    }
}
