@file:Suppress("TooGenericExceptionCaught", "MagicNumber")

package com.nighthawkapps.lib.android.sdk.wallet

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nighthawkapps.lib.android.sdk.util.SecureDataStoreSerializer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.security.SecureRandom

/**
 * Independent **`wallet_pass`** for **`Drk::new`** (turso/aegis256 key material).
 * Generated once per install and stored in secure datastore (not derived from the seed).
 */
internal object DrkWalletPassStore {
    private const val PREFS_FILE = "darkfi_wallet_secure.preferences_pb"
    private val KEY_PASS = stringPreferencesKey("drk_wallet_pass")

    @Volatile private var dataStoreInstance: DataStore<Preferences>? = null

    private fun getDataStore(context: Context): DataStore<Preferences> {
        val app = context.applicationContext
        return dataStoreInstance ?: synchronized(this) {
            dataStoreInstance ?: DataStoreFactory
                .create(
                    serializer = SecureDataStoreSerializer(SecureDataStoreSerializer.createAead(app, "darkfi_wallet")),
                    produceFile = { File(app.filesDir, "datastore/$PREFS_FILE") }
                ).also { dataStoreInstance = it }
        }
    }

    fun getOrCreate(context: Context): String =
        runBlocking {
            val ds = getDataStore(context)
            val prefs = ds.data.first()
            val existing = prefs[KEY_PASS]
            if (existing != null) {
                return@runBlocking existing
            }
            val generated =
                Base64.encodeToString(
                    ByteArray(32).also { SecureRandom().nextBytes(it) },
                    Base64.NO_WRAP,
                )
            ds.edit { it[KEY_PASS] = generated }
            generated
        }
}
