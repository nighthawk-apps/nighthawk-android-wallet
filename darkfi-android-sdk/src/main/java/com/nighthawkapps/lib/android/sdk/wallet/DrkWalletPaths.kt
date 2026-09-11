package com.nighthawkapps.lib.android.sdk.wallet

import android.content.Context
import java.io.File

/** App-private paths aligned with upstream **`bin/app/src/plugin/drk.rs`** on Android. */
object DrkWalletPaths {
    private const val ROOT = "drk"

    fun root(context: Context): File = File(context.applicationContext.filesDir, ROOT)

    fun walletDb(context: Context): File = File(root(context), "wallet.db")

    fun cacheDir(context: Context): File = File(root(context), "cache")

    fun ensureDirectories(context: Context) {
        cacheDir(context).mkdirs()
        root(context).mkdirs()
    }

    /**
     * Deletes the turso wallet DB (plus WAL/SHM sidecars) and the kvdb cache.
     * Used after a passphrase/schema mismatch so the next `Drk::new` can re-import keys
     * from the mnemonic. Does not touch the seed store.
     */
    fun wipeLocalState(context: Context) {
        val db = walletDb(context)
        db.parentFile
            ?.listFiles()
            ?.filter { it.isFile && it.name.startsWith("wallet.db") }
            ?.forEach { it.delete() }
        cacheDir(context).deleteRecursively()
        ensureDirectories(context)
    }
}
