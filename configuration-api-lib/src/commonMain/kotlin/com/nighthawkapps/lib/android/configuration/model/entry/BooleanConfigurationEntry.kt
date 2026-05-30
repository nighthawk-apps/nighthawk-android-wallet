package com.nighthawkapps.lib.android.configuration.model.entry

import com.nighthawkapps.lib.android.configuration.model.map.Configuration

data class BooleanConfigurationEntry(
    override val key: ConfigKey,
    private val defaultValue: Boolean
) : DefaultEntry<Boolean> {
    override fun getValue(configuration: Configuration) = configuration.getBoolean(key, defaultValue)
}
