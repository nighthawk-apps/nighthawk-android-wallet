package com.nighthawkapps.lib.android.preference.test.fixture

import com.nighthawkapps.lib.android.preference.model.entry.BooleanPreferenceDefault
import com.nighthawkapps.lib.android.preference.model.entry.PreferenceKey

object BooleanPreferenceDefaultFixture {
    val KEY = PreferenceKey("some_boolean_key") // $NON-NLS

    fun newTrue() = BooleanPreferenceDefault(KEY, true)

    fun newFalse() = BooleanPreferenceDefault(KEY, false)
}
