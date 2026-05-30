package com.nighthawkapps.lib.android.sdk.wallet

import android.app.Application
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DrkWalletPathsTest {
    private val context: Application
        get() = RuntimeEnvironment.getApplication() as Application

    @Test
    fun paths_liveUnderFilesDir() {
        val root = DrkWalletPaths.root(context)
        val db = DrkWalletPaths.walletDb(context)
        val cache = DrkWalletPaths.cacheDir(context)
        assertTrue(root.absolutePath.contains(context.filesDir.absolutePath))
        assertTrue(db.name == "wallet.db")
        assertTrue(cache.name == "cache")
    }

    @Test
    fun ensureDirectories_createsDrkTree() {
        DrkWalletPaths.ensureDirectories(context)
        assertTrue(DrkWalletPaths.root(context).isDirectory)
        assertTrue(DrkWalletPaths.cacheDir(context).isDirectory)
    }
}
