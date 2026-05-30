package com.nighthawkwallet.android

import android.app.Application
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SmallTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidApiTest {
    companion object {
        /** Must stay aligned with `ANDROID_TARGET_SDK_VERSION` in root `gradle.properties`. */
        private const val EXPECTED_TARGET_SDK = 37
    }

    @Test
    @SmallTest
    fun checkTargetApi() {
        assertEquals(
            EXPECTED_TARGET_SDK,
            ApplicationProvider.getApplicationContext<Application>().applicationInfo.targetSdkVersion,
        )
    }

    @Test
    @SmallTest
    fun checkMinApi() {
        // This test case prevents accidental release of the app with a different API level than we
        // have currently set in gradle.properties. It could impact the app's functionality. Don't
        // change this unless you're absolutely sure we're ready to set a new API level.
        assertEquals(
            ApplicationProvider.getApplicationContext<Application>().applicationInfo.minSdkVersion,
            Build.VERSION_CODES.O_MR1
        )
    }
}
