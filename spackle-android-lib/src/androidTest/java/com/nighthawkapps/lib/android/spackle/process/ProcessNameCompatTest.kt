package com.nighthawkapps.lib.android.spackle.process

import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SmallTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ProcessNameCompatTest {
    @SmallTest
    @Test
    fun searchForProcessName() {
        assertEquals(
            TEST_PACKAGE_PROCESS,
            ProcessNameCompat.searchForProcessNameLegacy(ApplicationProvider.getApplicationContext())
        )
    }

    @SmallTest
    @Test
    fun getProcessName() {
        assertEquals(
            TEST_PACKAGE_PROCESS,
            ProcessNameCompat.getProcessName(ApplicationProvider.getApplicationContext())
        )
    }

    companion object {
        const val TEST_PACKAGE_PROCESS = "com.nighthawkapps.lib.android.spackle.test"
    }
}
