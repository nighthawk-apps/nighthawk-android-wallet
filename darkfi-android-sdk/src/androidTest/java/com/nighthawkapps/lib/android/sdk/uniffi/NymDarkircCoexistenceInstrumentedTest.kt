package com.nighthawkapps.lib.android.sdk.uniffi

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nighthawkapps.lib.android.sdk.nym.PlatformTls
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.DarkircEventCallback
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.DarkircTransport
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.darkircConnectionPhase
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.darkircStatus
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.isNymRunning
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.sendChatMessage
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.startDarkirc
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.startNymProxy
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.stopDarkirc
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.stopNymProxy
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Default network setup on a live device: wallet traffic over the in-process Nym tunnel while
 * DarkIRC runs its own P2P transport in the same process. Needs internet; skipped if the mixnet
 * cannot bootstrap in time.
 */
@RunWith(AndroidJUnit4::class)
class NymDarkircCoexistenceInstrumentedTest {
    @Test
    fun darkircRoundTrip_whileNymTunnelServesHttps() {
        assumeTrue(DarkfiNativeProbe.run() is DarkfiNativeProbe.Ok)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(PlatformTls.ensureInitialized(context), "native TLS verifier init failed")

        startNymProxy(NYM_PORT.toString())
        try {
            assumeTrue("Nym mixnet did not bootstrap", awaitNym())
            assertEquals(200, httpsStatusOverSocks("https://api.ipify.org/"))

            val token = "nym-coexist-${System.currentTimeMillis()}"
            val latch = CountDownLatch(1)
            val callback =
                object : DarkircEventCallback {
                    override fun onMessage(
                        eventId: String,
                        channel: String,
                        nick: String,
                        message: String,
                        timestamp: ULong,
                        isOutgoing: Boolean,
                    ) {
                        if (message.contains(token)) latch.countDown()
                    }
                }
            val dbDir = File(context.cacheDir, "nym_darkirc_db_${System.currentTimeMillis()}").apply { mkdirs() }
            startDarkirc(dbDir.absolutePath, DarkircTransport.NYM, Integer.valueOf(NYM_PORT).toInt().toUShort(), callback)
            try {
                assertTrue(awaitDarkircRunning(), "darkirc never reached running state")
                assumeTrue("darkirc peers/DAG sync unavailable", awaitDarkircConnected())
                sendChatMessage("#nighthawk_nym", "nym_tester", "$token hello")
                assertTrue(latch.await(45, TimeUnit.SECONDS), "darkirc message not relayed back")
                assertTrue(isNymRunning(), "Nym tunnel went down while darkirc was running")
            } finally {
                stopDarkirc()
                dbDir.deleteRecursively()
            }
        } finally {
            stopNymProxy()
        }
    }

    private fun awaitNym(timeoutMs: Long = 180_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isNymRunning()) return true
            Thread.sleep(500)
        }
        return false
    }

    private fun awaitDarkircRunning(timeoutMs: Long = 90_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (darkircStatus() == "running") return true
            Thread.sleep(250)
        }
        return false
    }

    /** Sends are refused until the event graph has synced with real peers. */
    private fun awaitDarkircConnected(timeoutMs: Long = 300_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            when (darkircConnectionPhase()) {
                "connected" -> return true
                "failed" -> return false
            }
            Thread.sleep(1_000)
        }
        return false
    }

    /** Mixnet DNS occasionally times out and the tunnel is rebuilt, so allow a few attempts. */
    private fun httpsStatusOverSocks(url: String): Int {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("127.0.0.1", NYM_PORT))
        var last: Throwable? = null
        repeat(4) {
            try {
                val conn = URL(url).openConnection(proxy) as HttpsURLConnection
                conn.connectTimeout = 60_000
                conn.readTimeout = 60_000
                try {
                    return conn.responseCode
                } finally {
                    conn.disconnect()
                }
            } catch (e: java.io.IOException) {
                last = e
            }
        }
        throw AssertionError("HTTPS over Nym failed", last)
    }

    private companion object {
        const val NYM_PORT = 19180
    }
}
