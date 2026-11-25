package com.nighthawkapps.lib.android.ui.security

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    private val KEY_LOCKOUT_UNTIL_ELAPSED_MS = longPreferencesKey("pin_lockout_until_elapsed_ms")
    private val KEY_LOCKOUT_DURATION_MS = longPreferencesKey("pin_lockout_duration_ms")
    private val KEY_LOCKOUT_STEP = intPreferencesKey("pin_lockout_step")

    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 100_000
    private const val SALT_BYTES = 16
    private const val HASH_BYTES = 32
    private const val MAX_FAILURES = 5
    private const val BASE_LOCKOUT_MS = 30_000L
    private const val MAX_LOCKOUT_MS = 30L * 60L * 1000L
    private const val MAX_LOCKOUT_STEP = 6

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

    fun isConfiguredFlow(context: Context): Flow<Boolean> =
        getDataStore(context)
            .data
            .map { prefs -> !prefs[KEY_HASH].isNullOrBlank() }
            .distinctUntilChanged()

    fun isConfigured(context: Context): Boolean =
        runBlocking(Dispatchers.IO) {
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
        runBlocking(Dispatchers.IO) {
            val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
            val hash = hashPin(pin, salt)
            getDataStore(context).edit { prefs ->
                prefs[KEY_SALT] = Base64.encodeToString(salt, Base64.NO_WRAP)
                prefs[KEY_HASH] = Base64.encodeToString(hash, Base64.NO_WRAP)
                prefs[KEY_FAIL_COUNT] = 0
                prefs[KEY_LOCKOUT_STEP] = 0
                prefs[KEY_LOCKOUT_UNTIL_ELAPSED_MS] = 0L
                prefs[KEY_LOCKOUT_DURATION_MS] = 0L
            }
        }
    }

    fun verify(
        context: Context,
        pin: String,
    ): PinVerifyResult =
        runBlocking(Dispatchers.IO) {
            val ds = getDataStore(context)
            val prefs = ds.data.first()

            val now = SystemClock.elapsedRealtime()
            val lockoutUntil = prefs[KEY_LOCKOUT_UNTIL_ELAPSED_MS] ?: 0L
            val lockoutDuration = prefs[KEY_LOCKOUT_DURATION_MS] ?: 0L
            if (lockoutUntil > 0L && now < lockoutUntil) {
                val rawRemaining = lockoutUntil - now
                val remaining =
                    if (lockoutDuration > 0L && rawRemaining > lockoutDuration) {
                        // Device rebooted and reset elapsedRealtime(); re-anchor the lockout window.
                        ds.edit { it[KEY_LOCKOUT_UNTIL_ELAPSED_MS] = now + lockoutDuration }
                        lockoutDuration
                    } else {
                        rawRemaining
                    }
                return@runBlocking PinVerifyResult.LockedOut((remaining + 999) / 1000)
            }

            val saltB64 = prefs[KEY_SALT] ?: return@runBlocking PinVerifyResult.WrongPin
            val hashB64 = prefs[KEY_HASH] ?: return@runBlocking PinVerifyResult.WrongPin

            val salt = Base64.decode(saltB64, Base64.NO_WRAP)
            val expected = Base64.decode(hashB64, Base64.NO_WRAP)
            val actual = hashPin(pin, salt)

            if (MessageDigest.isEqual(expected, actual)) {
                ds.edit {
                    it[KEY_FAIL_COUNT] = 0
                    it[KEY_LOCKOUT_STEP] = 0
                    it[KEY_LOCKOUT_UNTIL_ELAPSED_MS] = 0L
                    it[KEY_LOCKOUT_DURATION_MS] = 0L
                }
                return@runBlocking PinVerifyResult.Success
            }

            val failures = (prefs[KEY_FAIL_COUNT] ?: 0) + 1
            if (failures >= MAX_FAILURES) {
                val step = (prefs[KEY_LOCKOUT_STEP] ?: 0).coerceIn(0, MAX_LOCKOUT_STEP)
                val durationMs = (BASE_LOCKOUT_MS shl step).coerceAtMost(MAX_LOCKOUT_MS)
                ds.edit {
                    it[KEY_FAIL_COUNT] = 0
                    it[KEY_LOCKOUT_STEP] = (step + 1).coerceAtMost(MAX_LOCKOUT_STEP)
                    it[KEY_LOCKOUT_UNTIL_ELAPSED_MS] = now + durationMs
                    it[KEY_LOCKOUT_DURATION_MS] = durationMs
                }
                return@runBlocking PinVerifyResult.LockedOut((durationMs + 999) / 1000)
            } else {
                ds.edit { it[KEY_FAIL_COUNT] = failures }
            }
            PinVerifyResult.WrongPin
        }

    fun lockoutRemainingSeconds(context: Context): Long =
        runBlocking(Dispatchers.IO) {
            val prefs = getDataStore(context).data.first()
            val lockoutUntil = prefs[KEY_LOCKOUT_UNTIL_ELAPSED_MS] ?: 0L
            val lockoutDuration = prefs[KEY_LOCKOUT_DURATION_MS] ?: 0L
            val now = SystemClock.elapsedRealtime()
            if (lockoutUntil <= 0L || now >= lockoutUntil) return@runBlocking 0L
            val rawRemaining = lockoutUntil - now
            val remaining =
                if (lockoutDuration > 0L) rawRemaining.coerceAtMost(lockoutDuration) else rawRemaining
            (remaining + 999) / 1000
        }

    fun clear(context: Context) {
        runBlocking(Dispatchers.IO) { getDataStore(context).edit { it.clear() } }
    }

    private fun hashPin(
        pin: String,
        salt: ByteArray,
    ): ByteArray {
        val chars = pin.toCharArray()
        val spec = PBEKeySpec(chars, salt, PBKDF2_ITERATIONS, HASH_BYTES * 8)
        return try {
            SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            chars.fill('\u0000')
        }
    }
}
