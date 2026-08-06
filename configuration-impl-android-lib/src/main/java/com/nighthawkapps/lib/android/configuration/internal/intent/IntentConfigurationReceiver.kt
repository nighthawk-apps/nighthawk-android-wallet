package com.nighthawkapps.lib.android.configuration.internal.intent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import com.nighthawkapps.lib.android.configuration.model.map.StringConfiguration
import kotlinx.collections.immutable.toPersistentMap
import kotlin.time.Clock

class IntentConfigurationReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context?,
        intent: Intent?,
    ) {
        val app = context?.applicationContext ?: return
        if ((app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
            return
        }
        intent?.defuse()?.let {
            val key = it.getStringExtra(ConfigurationIntent.EXTRA_STRING_KEY)
            val value = it.getStringExtra(ConfigurationIntent.EXTRA_STRING_VALUE)

            if (null != key) {
                val existingConfiguration = IntentConfigurationProvider.peekConfiguration().configurationMapping
                val newConfiguration =
                    if (null == value) {
                        existingConfiguration.remove(key)
                    } else {
                        existingConfiguration + (key to value)
                    }

                IntentConfigurationProvider.setConfiguration(
                    StringConfiguration(newConfiguration.toPersistentMap(), Clock.System.now())
                )
            }
        }
    }
}

// https://issuetracker.google.com/issues/36927401
private fun Intent.defuse(): Intent? =
    try {
        extras?.containsKey(null)
        this
    } catch (
        @Suppress("SwallowedException", "TooGenericExceptionCaught") e: Exception
    ) {
        null
    }
