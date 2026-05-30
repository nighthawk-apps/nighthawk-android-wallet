package com.nighthawkapps.lib.android.configuration.model.entry

import com.nighthawkapps.lib.android.configuration.test.MockConfiguration
import com.nighthawkapps.lib.android.configuration.test.fixture.StringDefaultEntryFixture
import kotlin.test.Test
import kotlin.test.assertEquals

class StringDefaultEntryTest {
    @Test
    fun key() {
        assertEquals(StringDefaultEntryFixture.KEY, StringDefaultEntryFixture.newEntryEntry().key)
    }

    @Test
    fun value_default() {
        val entry = StringDefaultEntryFixture.newEntryEntry()
        assertEquals(StringDefaultEntryFixture.DEFAULT_VALUE, entry.getValue(MockConfiguration()))
    }

    @Test
    fun value_override() {
        val entry = StringDefaultEntryFixture.newEntryEntry()
        assertEquals(
            "override",
            entry.getValue(MockConfiguration(mapOf(StringDefaultEntryFixture.KEY.key to "override")))
        )
    }
}
