package com.nighthawkapps.lib.android.ui.screen.support.model

import android.content.pm.PackageInfo
import com.nighthawkapps.lib.android.build.gitSha
import com.nighthawkapps.lib.android.spackle.versionCodeCompat

data class AppInfo(
    val versionName: String,
    val versionCode: Long,
    val gitSha: String
) {
    fun toSupportString() =
        buildString {
            appendLine("App version: $versionName ($versionCode) $gitSha")
        }

    companion object {
        fun new(packageInfo: PackageInfo) =
            AppInfo(
                packageInfo.versionName ?: "null", // Should only be null during tests
                packageInfo.versionCodeCompat,
                gitSha
            )
    }
}
