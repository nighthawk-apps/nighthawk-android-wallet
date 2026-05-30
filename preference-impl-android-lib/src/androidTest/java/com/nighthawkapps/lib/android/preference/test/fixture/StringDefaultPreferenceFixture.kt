package com.nighthawkapps.lib.android.preference.test.fixture

import com.nighthawkapps.lib.android.preference.model.entry.PreferenceKey
import com.nighthawkapps.lib.android.preference.model.entry.StringPreferenceDefault

object StringDefaultPreferenceFixture {
    val KEY = PreferenceKey("some_string_key") // $NON-NLS
    const val DEFAULT_VALUE = "some_default_value" // $NON-NLS

    fun new(
        preferenceKey: PreferenceKey = KEY,
        value: String = DEFAULT_VALUE
    ) = StringPreferenceDefault(preferenceKey, value)
}
