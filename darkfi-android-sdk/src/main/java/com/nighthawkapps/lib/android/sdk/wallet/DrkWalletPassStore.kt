@file:Suppress("TooGenericExceptionCaught", "MagicNumber")

package com.nighthawkapps.lib.android.sdk.wallet

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.nighthawkapps.lib.android.spackle.Twig
import java.security.SecureRandom

/**
 * SQLCipher **`wallet_pass`** for future **`Drk::new`**. Generated once per install and stored in
 * encrypted prefs (not derived from the seed — upstream expects an independent passphrase).
 */
internal object DrkWalletPassStore {
    private const val PREFS = "darkfi_wallet_encrypted"
    private const val KEY_PASS = "drk_wallet_pass"

    @Volatile private var instance: android.content.SharedPreferences? = null

    fun getOrCreate(context: Context): String {
        val app = context.applicationContext
        val prefs = instance ?: synchronized(this) {
            instance ?: try {
                val masterKey =
                    MasterKey
                        .Builder(app)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()
                EncryptedSharedPreferences.create(
                    app,
                    PREFS,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            } catch (e: Exception) {
                Twig.error(e) { "DrkWalletPassStore: encrypted prefs unavailable" }
                throw IllegalStateException("Failed to initialize secure preferences", e)
            }
        }.also { instance = it }
        prefs.getString(KEY_PASS, null)?.let { return it }
        val generated =
            Base64.encodeToString(
                ByteArray(32).also { SecureRandom().nextBytes(it) },
                Base64.NO_WRAP,
            )
        prefs.edit().putString(KEY_PASS, generated).apply()
        return generated
    }
}
