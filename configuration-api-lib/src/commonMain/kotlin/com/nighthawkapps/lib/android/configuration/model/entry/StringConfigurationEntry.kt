package com.nighthawkapps.lib.android.configuration.model.entry

import com.nighthawkapps.lib.android.configuration.model.map.Configuration

data class StringConfigurationEntry(
    override val key: ConfigKey,
    private val defaultValue: String
) : DefaultEntry<String> {
    override fun getValue(configuration: Configuration) = configuration.getString(key, defaultValue)
}
