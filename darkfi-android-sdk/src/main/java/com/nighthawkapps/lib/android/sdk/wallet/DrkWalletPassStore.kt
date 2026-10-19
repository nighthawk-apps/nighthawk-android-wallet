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

    /**
     * Database key derived from the mnemonic. The Android Keystore copy used to
     * come back empty on the emulator, so every cold start generated a new pass
     * and turso reported `Decryption failed for page=1`.
     */
    fun forMnemonic(words: List<String>): String {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(
            javax.crypto.spec.SecretKeySpec(
                "nighthawk-drk-wallet-pass-v1".toByteArray(Charsets.UTF_8),
                "HmacSHA256",
            ),
        )
        val phrase = words.joinToString(" ") { it.trim() }.toByteArray(Charsets.UTF_8)
        return Base64.encodeToString(mac.doFinal(phrase), Base64.NO_WRAP)
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
