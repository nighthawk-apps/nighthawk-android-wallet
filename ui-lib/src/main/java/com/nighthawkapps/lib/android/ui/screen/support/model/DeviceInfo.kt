package com.nighthawkapps.lib.android.ui.screen.support.model

import android.os.Build

data class DeviceInfo(
    val manufacturer: String,
    val device: String,
    val model: String
) {
    fun toSupportString() =
        buildString {
            appendLine("Device: $manufacturer $device $model")
        }

    companion object {
        fun new() = DeviceInfo(Build.MANUFACTURER, Build.DEVICE, Build.MODEL)
    }
}
