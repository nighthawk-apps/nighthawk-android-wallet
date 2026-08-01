package com.nighthawkapps.lib.android.preference

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nighthawkapps.lib.android.preference.api.PreferenceProvider
import com.nighthawkapps.lib.android.preference.model.entry.PreferenceKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class DataStorePreferenceProvider(
    private val dataStore: DataStore<Preferences>
) : PreferenceProvider {
    override suspend fun hasKey(key: PreferenceKey): Boolean {
        val prefs = dataStore.data.first()
        return prefs.contains(stringPreferencesKey(key.key))
    }

    override suspend fun putString(
        key: PreferenceKey,
        value: String?
    ) {
        dataStore.edit { prefs ->
            val prefKey = stringPreferencesKey(key.key)
            if (value == null) {
                prefs.remove(prefKey)
            } else {
                prefs[prefKey] = value
            }
        }
    }

    override suspend fun getString(key: PreferenceKey): String? {
        val prefs = dataStore.data.first()
        return prefs[stringPreferencesKey(key.key)]
    }

    override fun observe(key: PreferenceKey): Flow<String?> =
        dataStore.data.map { prefs ->
            prefs[stringPreferencesKey(key.key)]
        }
}
