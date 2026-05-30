package com.nighthawkapps.lib.android.preference.model.entry

import com.nighthawkapps.lib.android.preference.test.MockPreferenceProvider
import com.nighthawkapps.lib.android.preference.test.fixture.IntegerPreferenceDefaultFixture
import com.nighthawkapps.lib.android.preference.test.fixture.StringDefaultPreferenceFixture
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class IntegerPreferenceDefaultTest {
    @Test
    fun key() {
        assertEquals(IntegerPreferenceDefaultFixture.KEY, IntegerPreferenceDefaultFixture.new().key)
    }

    @Test
    fun value_default() =
        runTest {
            val entry = IntegerPreferenceDefaultFixture.new()
            assertEquals(IntegerPreferenceDefaultFixture.DEFAULT_VALUE, entry.getValue(MockPreferenceProvider()))
        }

    @Test
    fun value_override() =
        runTest {
            val expected = IntegerPreferenceDefaultFixture.DEFAULT_VALUE + 5

            val entry = IntegerPreferenceDefaultFixture.new()
            val mockPreferenceProvider =
                MockPreferenceProvider {
                    mutableMapOf(StringDefaultPreferenceFixture.KEY.key to expected.toString())
                }

            assertEquals(expected, entry.getValue(mockPreferenceProvider))
        }
}
