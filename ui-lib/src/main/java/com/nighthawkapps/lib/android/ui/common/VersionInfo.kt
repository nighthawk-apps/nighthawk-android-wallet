package com.nighthawkapps.lib.android.ui.common

import android.content.Context
import android.content.pm.ApplicationInfo
import com.nighthawkapps.lib.android.build.gitCommitCount
import com.nighthawkapps.lib.android.build.gitSha
import com.nighthawkapps.lib.android.spackle.EmulatorWtfUtil
import com.nighthawkapps.lib.android.spackle.FirebaseTestLabUtil
import com.nighthawkapps.lib.android.spackle.getPackageInfoCompat
import com.nighthawkapps.lib.android.spackle.versionCodeCompat

data class VersionInfo(
    val versionName: String,
    val versionCode: Long,
    val isDebuggable: Boolean,
    val gitSha: String,
    val gitCommitCount: Long
) {
    companion object {
        fun new(context: Context): VersionInfo {
            val packageInfo = context.packageManager.getPackageInfoCompat(context.packageName, 0L)
            val applicationInfo = context.applicationInfo

            return VersionInfo(
                versionName = packageInfo.versionName ?: "null", // Should only be null during tests
                versionCode = packageInfo.versionCodeCompat, // Should only be 0 during tests
                isDebuggable = (
                    (0 != applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) &&
                        !FirebaseTestLabUtil.isFirebaseTestLab(context.applicationContext) &&
                        !EmulatorWtfUtil.isEmulatorWtf(context.applicationContext)
                ),
                gitSha = gitSha,
                gitCommitCount = gitCommitCount.toLong()
            )
        }
    }
}
