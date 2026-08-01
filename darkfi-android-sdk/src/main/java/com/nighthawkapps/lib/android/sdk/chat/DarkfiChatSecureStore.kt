package com.nighthawkapps.lib.android.sdk.chat

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nighthawkapps.lib.android.sdk.util.SecureDataStoreSerializer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * AES-backed secure datastore for chat secrets (identity phrase, public id fingerprint, IRC PASS).
 */
internal object DarkfiChatSecureStore {
    const val KEY_SECRET_PHRASE: String = "secret_phrase_words"
    const val KEY_PUBLIC_ID: String = "public_id_sha_hex"
    const val KEY_IRC_PASSWORD: String = "darkfi_irc_password"

    private const val PREFS_FILE = "darkfi_chat_secure.preferences_pb"

    @Volatile private var dataStoreInstance: DataStore<Preferences>? = null

    private fun getDataStore(context: Context): DataStore<Preferences> {
        val app = context.applicationContext
        return dataStoreInstance ?: synchronized(this) {
            dataStoreInstance ?: DataStoreFactory
                .create(
                    serializer = SecureDataStoreSerializer(SecureDataStoreSerializer.createAead(app, "darkfi_chat")),
                    produceFile = { File(app.filesDir, "datastore/$PREFS_FILE") }
                ).also { dataStoreInstance = it }
        }
    }

    fun getString(
        context: Context,
        key: String
    ): String? =
        runBlocking {
            getDataStore(context).data.first()[stringPreferencesKey(key)]
        }

    fun putString(
        context: Context,
        key: String,
        value: String
    ) {
        runBlocking {
            getDataStore(context).edit { it[stringPreferencesKey(key)] = value }
        }
    }

    fun remove(
        context: Context,
        vararg keys: String
    ) {
        runBlocking {
            getDataStore(context).edit { prefs ->
                keys.forEach { prefs.remove(stringPreferencesKey(it)) }
            }
        }
    }

    fun clear(context: Context) {
        runBlocking {
            getDataStore(context).edit { it.clear() }
        }
    }
}
