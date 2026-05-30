package com.nighthawkapps.lib.android.sdk.uniffi

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.bridgePing
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.bridgeVersion
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * JNI round-trip for `libdarkfi_mobile_ffi` on device/emulator (requires jniLibs ABI match).
 */
@RunWith(AndroidJUnit4::class)
class DarkfiMobileFfiInstrumentedTest {
    @Test
    fun nativeProbe_and_bridge_roundTrip() {
        assumeTrue(DarkfiNativeProbe.run() is DarkfiNativeProbe.Ok)

        assertEquals("pong", bridgePing())
        assertTrue(bridgeVersion().isNotBlank())
    }
}
