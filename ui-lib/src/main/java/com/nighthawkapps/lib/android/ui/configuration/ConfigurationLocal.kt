package com.nighthawkapps.lib.android.ui.configuration

import androidx.compose.runtime.compositionLocalOf
import com.nighthawkapps.lib.android.configuration.model.map.Configuration
import com.nighthawkapps.lib.android.configuration.model.map.StringConfiguration
import kotlinx.collections.immutable.persistentMapOf

@Suppress("CompositionLocalAllowlist")
val RemoteConfig = compositionLocalOf<Configuration> { StringConfiguration(persistentMapOf(), null) }
