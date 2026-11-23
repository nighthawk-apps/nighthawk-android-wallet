package com.nighthawkapps.lib.android.sdk.mesh

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/**
 * Policy + lab gate for two-phone radio. Emulator BLE is not dual-role;
 * live GATT between two adapters is skipped unless this is a physical
 * device with Bluetooth on. Hardware chat still needs a second phone.
 */
@RunWith(AndroidJUnit4::class)
class MeshTwoPhoneInstrumentedTest {
    private val noisePayload = byteArrayOf(0x6E, 0x68, 0x01, SEND_MARK)

    private companion object {
        const val SEND_MARK: Byte = 0x51
    }

    @Test
    fun gattUuidsAreNotBitchat() {
        assertFalse(
            MeshGattConstants.SERVICE_UUID.toString().startsWith("f47b5e2d", ignoreCase = true),
        )
        assertEquals(
            "6e686d73-0001-4000-a000-4e6967687468",
            MeshGattConstants.SERVICE_UUID.toString(),
        )
        assertEquals(
            "6e686d73-0002-4000-a000-4e6967687468",
            MeshGattConstants.CHARACTERISTIC_UUID.toString(),
        )
    }

    @Test
    fun meshServiceIsConnectedDeviceAndNeverForLocation() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val info =
            ctx.packageManager.getServiceInfo(
                android.content.ComponentName(ctx, NighthawkMeshService::class.java),
                0,
            )
        assertFalse(info.exported)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            assertEquals(
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
                info.foregroundServiceType,
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val pkg =
                ctx.packageManager.getPackageInfo(
                    ctx.packageName,
                    PackageManager.GET_PERMISSIONS,
                )
            val perms = pkg.requestedPermissions ?: emptyArray()
            val flags = pkg.requestedPermissionsFlags ?: intArrayOf()
            val scan = perms.indexOf(Manifest.permission.BLUETOOTH_SCAN)
            assertTrue(scan >= 0)
            assertTrue(
                scan < flags.size &&
                    flags[scan] and PackageInfo.REQUESTED_PERMISSION_NEVER_FOR_LOCATION != 0,
            )
        }
        assertFalse(NighthawkMeshService.startFromForeground(ctx))
    }

    @Test
    fun liveTwoAdapterRadioIsSkippedOnEmulator() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter
        assumeTrue(
            "physical device with Bluetooth required for two-phone radio",
            adapter != null &&
                adapter.isEnabled &&
                !isEmulator(),
        )
        // Dual-role on one adapter is not an honest two-phone test.
        assertTrue(adapter!!.isEnabled)
    }

    /**
     * Run this on both phones at once. Each side speaks the production
     * [NighthawkBleLink], writes one Noise frame to the peer it connected,
     * and passes only when that peer's frame lands in [MeshFrameInbox].
     */
    @Test
    fun liveGattNoiseFrameArrivesFromTheOtherPhone() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter
        assumeTrue(
            "physical device with Bluetooth required",
            adapter != null && adapter.isEnabled && !isEmulator(),
        )
        val role =
            InstrumentationRegistry.getArguments().getString("mesh.role") ?: "listen"
        val sending = role == "send"
        val received = Collections.synchronizedList(ArrayList<ByteArray>())
        val inbox = MeshFrameInbox()
        val link =
            NighthawkBleLink(ctx) { frame ->
                if (inbox.shouldIngest(frame)) received.add(frame)
            }
        link.setPower(foreground = true, charging = false, gatewayArmed = false, alwaysOn = true)
        val sends = AtomicInteger(0)
        link.start()
        try {
            // Stay up for the whole window. Leaving as soon as one side
            // receives tears the radio down before the other side's write lands.
            val deadline = System.currentTimeMillis() + 45_000L
            while (System.currentTimeMillis() < deadline) {
                if (sending) maybeSendNoise(link, sends)
                Thread.sleep(500L)
            }
        } finally {
            link.stop()
        }
        if (sending) {
            assertTrue(
                "sender never wrote a Noise frame (peers=${link.peerCount})",
                sends.get() > 0,
            )
            return
        }
        assertTrue(
            "GATT peer did not deliver a Noise frame (peers=${link.peerCount})",
            received.isNotEmpty(),
        )
        val pkt = MeshWireCodec.decode(received[0])
        assertEquals(MeshWireCodec.NH_NOISE_ENC, pkt?.type)
        assertEquals(SEND_MARK.toInt(), pkt?.payload?.lastOrNull()?.toInt())
        assertTrue(inbox.shouldIngest(received[0]))
    }

    private fun maybeSendNoise(
        link: NighthawkBleLink,
        sends: AtomicInteger,
    ) {
        if (sends.get() >= 40 || link.peerCount <= 0) return
        val sender = link.sessionHint.copyOf()
        if (sender.all { it == 0.toByte() }) return
        var wrote = false
        for (recipientHex in link.meshToAtt.keys) {
            val frame = encodeNoiseFrame(sender, recipientHex) ?: continue
            link.send(frame)
            wrote = true
        }
        if (wrote) sends.incrementAndGet()
    }

    private fun encodeNoiseFrame(
        sender: ByteArray,
        recipientHex: String,
    ): ByteArray? {
        val recipient = hexToBytes(recipientHex)
        if (recipient.size != MeshWireCodec.SENDER_LEN) return null
        return MeshWireCodec.encode(
            MeshWirePacket(
                type = MeshWireCodec.NH_NOISE_ENC,
                ttl = 7,
                flags = 0,
                timestampMs = 0L,
                sender = sender,
                recipient = recipient,
                payload = noisePayload,
            ),
        )
    }

    private fun hexToBytes(hex: String): ByteArray {
        if (hex.length % 2 != 0) return ByteArray(0)
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    private fun isEmulator(): Boolean {
        val fp = Build.FINGERPRINT.lowercase()
        return fp.contains("generic") ||
            fp.contains("emulator") ||
            Build.MODEL.contains("Emulator", ignoreCase = true) ||
            Build.PRODUCT.contains("sdk", ignoreCase = true)
    }
}
