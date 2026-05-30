package com.nighthawkapps.lib.android.configuration.test.fixture

import com.nighthawkapps.lib.android.configuration.model.entry.ConfigKey
import com.nighthawkapps.lib.android.configuration.model.entry.IntegerConfigurationEntry

object IntegerDefaultEntryFixture {
    val KEY = ConfigKey("some_string_key") // $NON-NLS
    const val DEFAULT_VALUE = 123

    fun newEntry(
        key: ConfigKey = KEY,
        value: Int = DEFAULT_VALUE
    ) = IntegerConfigurationEntry(key, value)
}
