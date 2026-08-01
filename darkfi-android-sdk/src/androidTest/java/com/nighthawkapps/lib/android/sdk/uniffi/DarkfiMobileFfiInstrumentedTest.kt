package com.nighthawkapps.lib.android.sdk.uniffi

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.DarkfiWalletNativeException
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.DarkircEventCallback
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.bridgePing
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.bridgeVersion
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.darkircStatus
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.generateDarkfiMnemonic
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.sendChatMessage
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.startDarkirc
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.stopDarkirc
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

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

    @Test
    fun generateDarkfiMnemonic_returns22Words() {
        assumeTrue(DarkfiNativeProbe.run() is DarkfiNativeProbe.Ok)
        val words = generateDarkfiMnemonic()
        assertEquals(22, words.size)
        assertTrue(words.all { it.isNotBlank() })
    }

    @Test
    fun darkircLifecycle_startTwiceThrows_sendWhenNotRunningThrows() {
        assumeTrue(DarkfiNativeProbe.run() is DarkfiNativeProbe.Ok)
        
        // Ensure we start from a clean state
        val initialStatus = darkircStatus()
        if (initialStatus == "running" || initialStatus == "starting") {
            stopDarkirc()
        }

        // Test sending message when not running
        assertFailsWith<DarkfiWalletNativeException.NativeDrkUnavailable> {
            sendChatMessage("#test", "nick", "hello")
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbDir = File(context.cacheDir, "test_darkirc_db_${System.currentTimeMillis()}")
        dbDir.mkdirs()

        val callback = object : DarkircEventCallback {
            override fun onMessage(
                eventId: String,
                channel: String,
                nick: String,
                message: String,
                timestamp: ULong,
            ) {
                // Ignore
            }
        }

        // Start daemon
        val zeroUShort: UShort = 0u
        startDarkirc(dbDir.absolutePath, false, zeroUShort, callback)
        
        val status = darkircStatus()
        assertTrue(status == "starting" || status == "running")

        // Starting twice should throw
        assertFailsWith<DarkfiWalletNativeException.NativeDrkUnavailable> {
            val zeroUShort: UShort = 0u
            startDarkirc(dbDir.absolutePath, false, zeroUShort, callback)
        }

        // Stop daemon
        stopDarkirc()
        
        val statusAfterStop = darkircStatus()
        assertTrue(statusAfterStop == "stopping" || statusAfterStop == "not_running")
    }

    /**
     * Regression test for the missing-public-channel-messages bug.
     *
     * A `Privmsg` sent on a public channel is serialized, inserted into the
     * event graph DAG, and relayed back through `event_pub` to the registered
     * callback. This exercises the exact serialize -> DAG -> deserialize path
     * for public channels against the rebuilt native library, independent of
     * any remote peers. Before the fix (the `Privmsg` struct was missing the
     * upstream `version`/`msg_type` prefix, the send path used the channel name
     * as the DAG key, and never inserted the event header) no public-channel
     * message could survive this round trip.
     */
    @Test
    fun publicChannelMessage_roundTripsThroughEventGraphToCallback() {
        assumeTrue(DarkfiNativeProbe.run() is DarkfiNativeProbe.Ok)

        awaitDaemonStopped()

        val channel = "#nighthawk_loopback"
        val nick = "loopback_tester"
        val token = "pub-chan-token-${System.currentTimeMillis()}"

        val received = AtomicReference<Triple<String, String, String>?>(null)
        val latch = CountDownLatch(1)

        val callback = object : DarkircEventCallback {
            override fun onMessage(
                eventId: String,
                channel: String,
                nick: String,
                message: String,
                timestamp: ULong,
            ) {
                if (message.contains(token)) {
                    received.set(Triple(channel, nick, message))
                    latch.countDown()
                }
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbDir = File(context.cacheDir, "loopback_darkirc_db_${System.currentTimeMillis()}")
        dbDir.mkdirs()

        val zeroUShort: UShort = 0u
        startDarkirc(dbDir.absolutePath, false, zeroUShort, callback)
        try {
            assertTrue(awaitDaemonRunning(), "daemon never reached running state")

            sendChatMessage(channel, nick, "$token hello public channel")

            val delivered = latch.await(45, TimeUnit.SECONDS)
            assertTrue(delivered, "public channel message was not relayed back to the callback")

            val (gotChannel, gotNick, gotMsg) = received.get()!!
            assertEquals(channel, gotChannel)
            assertEquals(nick, gotNick)
            assertTrue(gotMsg.contains(token))
        } finally {
            stopDarkirc()
        }
    }

    private fun awaitDaemonRunning(timeoutMs: Long = 90_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            when (darkircStatus()) {
                "running" -> return true
                "failed", "not_running" -> { /* keep waiting briefly in case of late transition */ }
            }
            Thread.sleep(250)
        }
        return darkircStatus() == "running"
    }

    private fun awaitDaemonStopped(timeoutMs: Long = 30_000) {
        if (darkircStatus() == "not_running") return
        try {
            stopDarkirc()
        } catch (_: Throwable) {
            // Already stopped or never started.
        }
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline && darkircStatus() != "not_running") {
            Thread.sleep(250)
        }
    }
}
