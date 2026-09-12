@file:Suppress("MagicNumber")

package com.nighthawkapps.lib.android.ui.screen.support.model

import android.content.pm.PackageInfo
import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

data class TimeInfo(
    val currentTime: Instant,
    val rebootTime: Instant,
    val installTime: Instant,
    val updateTime: Instant
) {
    fun toSupportString() =
        buildString {
            // Use a slightly more human friendly format instead of ISO, since this will appear in the emails that users see
            val dateFormat = SimpleDateFormat("yyyy-MM-dd hh:mm:ss a", Locale.US) // $NON-NLS-1$

            appendLine("Current time: ${dateFormat.formatInstant(currentTime.fuzz())}")
            appendLine("Reboot time: ${dateFormat.formatInstant(rebootTime.fuzz())}")
            appendLine("Install time: ${dateFormat.formatInstant(installTime.fuzz())}")
            appendLine("Update time: ${dateFormat.formatInstant(updateTime.fuzz())}")
        }

    companion object {
        fun new(packageInfo: PackageInfo): TimeInfo {
            val currentTime = Clock.System.now()
            val elapsedRealtime = SystemClock.elapsedRealtime().milliseconds

            return TimeInfo(
                currentTime = currentTime,
                rebootTime = currentTime - elapsedRealtime,
                installTime = Instant.fromEpochMilliseconds(packageInfo.firstInstallTime),
                updateTime = Instant.fromEpochMilliseconds(packageInfo.lastUpdateTime)
            )
        }
    }
}

private fun SimpleDateFormat.formatInstant(instant: Instant) = format(Date(instant.toEpochMilliseconds()))

private fun Instant.fuzz(): Instant {
    val epochMillis = this.toEpochMilliseconds()
    val fiveMinutesMillis = 5 * 60 * 1000L
    return Instant.fromEpochMilliseconds((epochMillis / fiveMinutesMillis) * fiveMinutesMillis)
}
