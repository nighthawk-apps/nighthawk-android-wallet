package com.nighthawkapps.lib.android.preference.test.fixture

import com.nighthawkapps.lib.android.preference.model.entry.IntegerPreferenceDefault
import com.nighthawkapps.lib.android.preference.model.entry.PreferenceKey

object IntegerPreferenceDefaultFixture {
    val KEY = PreferenceKey("some_string_key") // $NON-NLS
    const val DEFAULT_VALUE = 123

    fun new(
        preferenceKey: PreferenceKey = KEY,
        value: Int = DEFAULT_VALUE
    ) = IntegerPreferenceDefault(preferenceKey, value)
}
