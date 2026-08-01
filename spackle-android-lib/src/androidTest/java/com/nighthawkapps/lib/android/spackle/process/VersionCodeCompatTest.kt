package com.nighthawkapps.lib.android.spackle.process

import android.content.pm.PackageInfo
import androidx.test.filters.SmallTest
import com.nighthawkapps.lib.android.spackle.AndroidApiVersion
import com.nighthawkapps.lib.android.spackle.versionCodeCompat
import org.junit.Assert.assertEquals
import org.junit.Test

class VersionCodeCompatTest {
    @Test
    @SmallTest
    fun versionCodeCompat() {
        val expectedVersionCode = 123L

        val packageInfo =
            PackageInfo().apply {
                @Suppress("Deprecation")
                versionCode = expectedVersionCode.toInt()
                if (AndroidApiVersion.isAtLeastT) {
                    longVersionCode = expectedVersionCode
                }
            }

        assertEquals(expectedVersionCode, packageInfo.versionCodeCompat)
    }
}
