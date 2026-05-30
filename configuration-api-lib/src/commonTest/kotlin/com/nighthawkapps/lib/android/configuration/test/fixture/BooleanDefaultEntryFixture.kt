package com.nighthawkapps.lib.android.configuration.test.fixture

import com.nighthawkapps.lib.android.configuration.model.entry.BooleanConfigurationEntry
import com.nighthawkapps.lib.android.configuration.model.entry.ConfigKey

object BooleanDefaultEntryFixture {
    val KEY = ConfigKey("some_boolean_key") // $NON-NLS

    fun newTrueEntry() = BooleanConfigurationEntry(KEY, true)

    fun newFalseEntry() = BooleanConfigurationEntry(KEY, false)
}
