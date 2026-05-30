package com.nighthawkapps.lib.android.ui.preference

import androidx.test.filters.SmallTest
import com.nighthawkapps.lib.android.preference.model.entry.PreferenceDefault
import org.junit.Test
import kotlin.reflect.full.memberProperties
import kotlin.test.assertFalse

class EncryptedPreferenceKeysTest {
    // This test is primary to prevent copy-paste errors in preference keys
    @SmallTest
    @Test
    fun unique_keys() {
        val fieldValueSet = mutableSetOf<String>()

        EncryptedPreferenceKeys::class
            .memberProperties
            .map { it.getter.call(EncryptedPreferenceKeys) }
            .map { it as PreferenceDefault<*> }
            .map { it.key }
            .forEach {
                assertFalse(fieldValueSet.contains(it.key), "Duplicate key $it")

                fieldValueSet.add(it.key)
            }
    }
}
