package com.nighthawkapps.lib.android.sdk.uniffi

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.DarkfiWalletNativeException
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.DarkircEventCallback
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.DarkircTransport
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.bridgePing
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.bridgeVersion
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.darkircConnectionPhase
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

        // stopDarkirc() returns while status is still "stopping". A later
        // start is rejected until the daemon thread reaches not_running.
        awaitDaemonStopped()

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
                isOutgoing: Boolean,
            ) {
                // Ignore
            }
        }

        // Start daemon
        val zeroUShort: UShort = 0u
        startDarkirc(dbDir.absolutePath, DarkircTransport.DIRECT, zeroUShort, callback)
        
        val status = darkircStatus()
        assertTrue(status == "starting" || status == "running")

        // Starting twice should throw
        assertFailsWith<DarkfiWalletNativeException.NativeDrkUnavailable> {
            val zeroUShort: UShort = 0u
            startDarkirc(dbDir.absolutePath, DarkircTransport.DIRECT, zeroUShort, callback)
        }

        // Stop daemon. stopDarkirc() returns before the thread is gone.
        stopDarkirc()

        val statusAfterStop = darkircStatus()
        assertTrue(statusAfterStop == "stopping" || statusAfterStop == "not_running")
        awaitDaemonStopped()
    }

    @Test
    fun darkirc_restarts_on_the_same_datastore_after_stop() {
        assumeTrue(DarkfiNativeProbe.run() is DarkfiNativeProbe.Ok)
        awaitDaemonStopped()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbDir = File(context.cacheDir, "test_darkirc_restart_${System.currentTimeMillis()}")
        dbDir.mkdirs()
        val callback = object : DarkircEventCallback {
            override fun onMessage(
                eventId: String,
                channel: String,
                nick: String,
                message: String,
                timestamp: ULong,
                isOutgoing: Boolean,
            ) {
            }
        }
        val port: UShort = 0u
        startDarkirc(dbDir.absolutePath, DarkircTransport.DIRECT, port, callback)
        assertTrue(awaitNotFailed(15_000), "first start failed: ${darkircStatus()} ${darkircConnectionPhase()}")
        stopDarkirc()
        awaitDaemonStopped()
        startDarkirc(dbDir.absolutePath, DarkircTransport.DIRECT, port, callback)
        assertTrue(
            awaitNotFailed(20_000),
            "restart after disconnect failed: ${darkircStatus()} ${darkircConnectionPhase()}",
        )
        stopDarkirc()
        awaitDaemonStopped()
    }

    /**
     * Regression test for the missing-public-channel-messages bug.
     *
     * A `Privmsg` sent on a public channel is serialized, inserted into the
     * event graph DAG, and relayed back through `event_pub` to the registered
     * callback. The native send path refuses the message until the DAG is
     * synced (`connected`), so this waits for that phase before sending.
     * Before the fix (the `Privmsg` struct was missing the upstream
     * `version`/`msg_type` prefix, the send path used the channel name as the
     * DAG key, and never inserted the event header) no public-channel message
     * could survive this round trip.
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
                isOutgoing: Boolean,
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
        startDarkirc(dbDir.absolutePath, DarkircTransport.DIRECT, zeroUShort, callback)
        try {
            assertTrue(awaitDaemonRunning(), "daemon never reached running state")
            assertTrue(
                awaitDaemonConnected(),
                "darkirc never reached connected; send is refused until the DAG is synced",
            )

            sendChatMessage(channel, nick, "$token hello public channel")

            val delivered = latch.await(45, TimeUnit.SECONDS)
            assertTrue(delivered, "public channel message was not relayed back to the callback")

            val (gotChannel, gotNick, gotMsg) = received.get()!!
            assertEquals(channel, gotChannel)
            assertEquals(nick, gotNick)
            assertTrue(gotMsg.contains(token))
        } finally {
            stopDarkirc()
            awaitDaemonStopped()
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

    /** Sends are refused until [darkircConnectionPhase] reports `connected`. */
    private fun awaitDaemonConnected(timeoutMs: Long = 180_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            when (darkircConnectionPhase()) {
                "connected" -> return true
                "failed" -> return false
            }
            val status = darkircStatus()
            if (status == "failed" || status == "not_running") return false
            Thread.sleep(250)
        }
        return darkircConnectionPhase() == "connected"
    }

    /** True once the daemon is starting or running, false if it lands on failed. */
    private fun awaitNotFailed(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            when (darkircStatus()) {
                "failed" -> return false
                "starting", "running" -> {
                    if (darkircConnectionPhase() == "failed") return false
                    return true
                }
            }
            Thread.sleep(200)
        }
        return darkircStatus() != "failed"
    }

    private fun awaitDaemonStopped(timeoutMs: Long = 60_000) {
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
