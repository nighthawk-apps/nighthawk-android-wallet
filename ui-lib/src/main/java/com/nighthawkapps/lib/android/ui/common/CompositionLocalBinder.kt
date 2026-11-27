package com.nighthawkapps.lib.android.ui.common

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.nighthawkapps.lib.android.spackle.EmulatorWtfUtil
import kotlinx.coroutines.flow.map

@Composable
fun ComponentActivity.BindCompLocalProvider(content: @Composable () -> Unit) {
    val screenSecurity = remember { ScreenSecurity() }
    val screenBrightness = remember { ScreenBrightness() }
    val screenTimeout = remember { ScreenTimeout() }

    LaunchedEffect(screenSecurity) {
        observeScreenSecurityFlag(screenSecurity)
    }
    LaunchedEffect(screenBrightness) {
        observeScreenBrightnessFlag(screenBrightness)
    }
    LaunchedEffect(screenTimeout) {
        observeScreenTimeoutFlag(screenTimeout)
    }
    CompositionLocalProvider(
        LocalScreenSecurity provides screenSecurity,
        LocalScreenBrightness provides screenBrightness,
        LocalScreenTimeout provides screenTimeout,
        content = content,
    )
}

private suspend fun ComponentActivity.observeScreenSecurityFlag(screenSecurity: ScreenSecurity) {
    screenSecurity.referenceCount
        .map { it > 0 }
        .collect { isSecure ->
            val isTest = EmulatorWtfUtil.isEmulatorWtf(applicationContext)

            if (isSecure && !isTest && shouldSecureScreen) {
                window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE,
                )
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
}

private suspend fun ComponentActivity.observeScreenBrightnessFlag(screenBrightness: ScreenBrightness) {
    screenBrightness.referenceCount
        .map { it > 0 }
        .collect { maxBrightness ->
            if (maxBrightness) {
                window.attributes =
                    window.attributes.apply {
                        this.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
                    }
            } else {
                window.attributes =
                    window.attributes.apply {
                        this.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    }
            }
        }
}

private suspend fun ComponentActivity.observeScreenTimeoutFlag(screenTimeout: ScreenTimeout) {
    screenTimeout.referenceCount
        .map { it > 0 }
        .collect { disableTimeout ->
            if (disableTimeout) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
}
