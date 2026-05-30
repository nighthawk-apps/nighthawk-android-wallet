package com.nighthawkapps.lib.android.configuration.model.entry

import com.nighthawkapps.lib.android.configuration.model.map.Configuration

data class IntegerConfigurationEntry(
    override val key: ConfigKey,
    private val defaultValue: Int
) : DefaultEntry<Int> {
    override fun getValue(configuration: Configuration) = configuration.getInt(key, defaultValue)
}
