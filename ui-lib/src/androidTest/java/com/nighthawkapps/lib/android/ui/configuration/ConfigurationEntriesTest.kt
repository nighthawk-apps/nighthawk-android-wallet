package com.nighthawkapps.lib.android.ui.configuration

import androidx.test.filters.SmallTest
import com.nighthawkapps.lib.android.configuration.model.entry.DefaultEntry
import org.junit.Test
import kotlin.reflect.full.memberProperties
import kotlin.test.assertFalse

class ConfigurationEntriesTest {
    // This test is primary to prevent copy-paste errors in configuration keys
    @SmallTest
    @Test
    fun keys_unique() {
        val fieldValueSet = mutableSetOf<String>()

        ConfigurationEntries::class
            .memberProperties
            .map { it.getter.call(ConfigurationEntries) }
            .map { it as DefaultEntry<*> }
            .map { it.key }
            .forEach {
                assertFalse(fieldValueSet.contains(it.key), "Duplicate key $it")

                fieldValueSet.add(it.key)
            }
    }
}
