package com.nighthawkapps.lib.android.configuration.test.fixture

import com.nighthawkapps.lib.android.configuration.model.entry.ConfigKey
import com.nighthawkapps.lib.android.configuration.model.entry.StringConfigurationEntry

object StringDefaultEntryFixture {
    val KEY = ConfigKey("some_string_key") // $NON-NLS
    const val DEFAULT_VALUE = "some_default_value" // $NON-NLS

    fun newEntryEntry(
        key: ConfigKey = KEY,
        value: String = DEFAULT_VALUE
    ) = StringConfigurationEntry(key, value)
}
