package com.nighthawkapps.lib.android.ui.security

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Stores a PBKDF2 hash of the wallet PIN in secure datastore. Plaintext PIN is never persisted.
 */
object WalletPinSecureStore {
    private const val PREFS_FILE = "wallet_pin_secure.preferences_pb"

    private val KEY_HASH = stringPreferencesKey("pin_hash_b64")
    private val KEY_SALT = stringPreferencesKey("pin_salt_b64")
    private val KEY_FAIL_COUNT = intPreferencesKey("pin_fail_count")
    private val KEY_LOCKOUT_UNTIL_MS = longPreferencesKey("pin_lockout_until_ms")

    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 100_000
    private const val SALT_BYTES = 16
    private const val HASH_BYTES = 32
    private const val MAX_FAILURES = 5
    private const val LOCKOUT_MS = 30_000L

    @Volatile private var dataStoreInstance: DataStore<Preferences>? = null

    private fun getDataStore(context: Context): DataStore<Preferences> {
        val app = context.applicationContext
        return dataStoreInstance ?: synchronized(this) {
            dataStoreInstance ?: DataStoreFactory
                .create(
                    serializer = SecureDataStoreSerializer(SecureDataStoreSerializer.createAead(app, "wallet_pin")),
                    produceFile = { File(app.filesDir, "datastore/$PREFS_FILE") }
                ).also { dataStoreInstance = it }
        }
    }

    sealed interface PinVerifyResult {
        data object Success : PinVerifyResult

        data object WrongPin : PinVerifyResult

        data class LockedOut(
            val remainingSeconds: Long
        ) : PinVerifyResult
    }

    fun isConfigured(context: Context): Boolean =
        runBlocking {
            getDataStore(context)
                .data
                .first()[KEY_HASH]
                .isNullOrBlank()
                .not()
        }

    fun setPin(
        context: Context,
        pin: String,
    ) {
        require(pin.length == 6 && pin.all { it.isDigit() }) { "PIN must be six digits" }
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = hashPin(pin, salt)
        runBlocking {
            getDataStore(context).edit { prefs ->
                prefs[KEY_SALT] = Base64.encodeToString(salt, Base64.NO_WRAP)
                prefs[KEY_HASH] = Base64.encodeToString(hash, Base64.NO_WRAP)
                prefs[KEY_FAIL_COUNT] = 0
                prefs[KEY_LOCKOUT_UNTIL_MS] = 0L
            }
        }
    }

    fun verify(
        context: Context,
        pin: String,
    ): PinVerifyResult =
        runBlocking {
            val ds = getDataStore(context)
            val prefs = ds.data.first()

            val now = System.currentTimeMillis()
            val lockoutUntil = prefs[KEY_LOCKOUT_UNTIL_MS] ?: 0L
            if (now < lockoutUntil) {
                return@runBlocking PinVerifyResult.LockedOut(((lockoutUntil - now) + 999) / 1000)
            }

            val saltB64 = prefs[KEY_SALT] ?: return@runBlocking PinVerifyResult.WrongPin
            val hashB64 = prefs[KEY_HASH] ?: return@runBlocking PinVerifyResult.WrongPin

            val salt = Base64.decode(saltB64, Base64.NO_WRAP)
            val expected = Base64.decode(hashB64, Base64.NO_WRAP)
            val actual = hashPin(pin, salt)

            if (MessageDigest.isEqual(expected, actual)) {
                ds.edit {
                    it[KEY_FAIL_COUNT] = 0
                    it[KEY_LOCKOUT_UNTIL_MS] = 0L
                }
                return@runBlocking PinVerifyResult.Success
            }

            val failures = (prefs[KEY_FAIL_COUNT] ?: 0) + 1
            if (failures >= MAX_FAILURES) {
                ds.edit {
                    it[KEY_FAIL_COUNT] = 0
                    it[KEY_LOCKOUT_UNTIL_MS] = now + LOCKOUT_MS
                }
            } else {
                ds.edit { it[KEY_FAIL_COUNT] = failures }
            }
            PinVerifyResult.WrongPin
        }

    fun lockoutRemainingSeconds(context: Context): Long =
        runBlocking {
            val prefs = getDataStore(context).data.first()
            val lockoutUntil = prefs[KEY_LOCKOUT_UNTIL_MS] ?: 0L
            val now = System.currentTimeMillis()
            if (now >= lockoutUntil) return@runBlocking 0L
            ((lockoutUntil - now) + 999) / 1000
        }

    fun clear(context: Context) {
        runBlocking { getDataStore(context).edit { it.clear() } }
    }

    private fun hashPin(
        pin: String,
        salt: ByteArray,
    ): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, HASH_BYTES * 8)
        return SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
    }
}
