package com.nighthawkapps.lib.android.preference

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.nighthawkapps.lib.android.preference.api.PreferenceProvider
import com.nighthawkapps.lib.android.preference.model.entry.PreferenceKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/**
 * Provides an Android implementation of shared preferences.
 *
 * This class is thread-safe.
 *
 * For a given preference file, it is expected that only a single instance is constructed and that
 * this instance lives for the lifetime of the application. Constructing multiple instances will
 * potentially corrupt preference data and will leak resources.
 */
class AndroidPreferenceProvider(
    private val sharedPreferences: SharedPreferences,
    private val dispatcher: CoroutineDispatcher
) : PreferenceProvider {
    override suspend fun hasKey(key: PreferenceKey) =
        withContext(dispatcher) {
            sharedPreferences.contains(key.key)
        }

    @SuppressLint("ApplySharedPref")
    override suspend fun putString(
        key: PreferenceKey,
        value: String?
    ) = withContext(dispatcher) {
        val editor = sharedPreferences.edit()

        editor.putString(key.key, value)

        editor.commit()

        Unit
    }

    override suspend fun getString(key: PreferenceKey) =
        withContext(dispatcher) {
            sharedPreferences.getString(key.key, null)
        }

    override fun observe(key: PreferenceKey): Flow<String?> =
        callbackFlow<Unit> {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                    // Callback on main thread
                    trySend(Unit)
                }
            sharedPreferences.registerOnSharedPreferenceChangeListener(listener)

            // Kickstart the emissions
            trySend(Unit)

            awaitClose {
                sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }.flowOn(dispatcher)
            .map { getString(key) }

    companion object {
        suspend fun newStandard(
            context: Context,
            filename: String
        ): PreferenceProvider {
            val singleThreadedDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

            val sharedPreferences =
                withContext(singleThreadedDispatcher) {
                    context.getSharedPreferences(filename, Context.MODE_PRIVATE)
                }

            return AndroidPreferenceProvider(sharedPreferences, singleThreadedDispatcher)
        }

        suspend fun newEncrypted(
            context: Context,
            filename: String
        ): PreferenceProvider {
            val aead = SecurePreferencesSerializer.createAead(context, filename)
            val dataStore =
                androidx.datastore.core.DataStoreFactory.create(
                    serializer = SecurePreferencesSerializer(aead),
                    produceFile = { File(context.filesDir, "datastore/$filename.preferences_pb") }
                )
            return DataStorePreferenceProvider(dataStore)
        }
    }
}
