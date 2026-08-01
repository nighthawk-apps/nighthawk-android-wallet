package com.nighthawkapps.lib.android.ui.screen.support.model

import com.nighthawkapps.lib.android.configuration.api.ConfigurationProvider
import kotlinx.datetime.Instant

data class ConfigInfo(
    val configurationUpdatedAt: Instant?
) {
    fun toSupportString() =
        buildString {
            appendLine("Configuration: $configurationUpdatedAt")
        }

    companion object {
        fun new(configurationProvider: ConfigurationProvider) =
            ConfigInfo(
                configurationProvider.peekConfiguration().updatedAt
            )
    }
}
