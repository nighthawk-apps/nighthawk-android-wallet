package com.nighthawkapps.lib.android.sdk.mesh

import android.content.Intent
import android.net.NetworkCapabilities
import android.os.BatteryManager

/** Pure helpers so gateway arming can be unit-tested without a radio. */
object MeshEnvironment {
    fun isCharging(batteryChanged: Intent?): Boolean {
        if (batteryChanged == null) return false
        val plugged = batteryChanged.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        return plugged != 0
    }

    fun isUnmeteredWifi(caps: NetworkCapabilities?): Boolean {
        if (caps == null) return false
        val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        val internet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        return wifi && unmetered && internet
    }
}

fun interface MeshLinkSink {
    fun onLinkFrame(frame: ByteArray)
}

/** Validates inbound frames and drops forbidden LWD methods before FFI. */
class MeshFrameInbox(
    val dag: MeshDagBridge = MeshDagBridge(),
) : MeshLinkSink {
    private val frames = ArrayDeque<ByteArray>()

    /** False when the OS layer must not call `MeshNative.ingest`. */
    fun shouldIngest(frame: ByteArray): Boolean {
        val pkt = MeshWireCodec.decode(frame) ?: return false
        return when (dag.consider(pkt)) {
            MeshDagAdmit.RejectedOversized, MeshDagAdmit.RejectedForbiddenLwd -> false
            MeshDagAdmit.Admitted, MeshDagAdmit.Ignored ->
                when (pkt.type) {
                    MeshWireCodec.NH_NOISE_HS,
                    MeshWireCodec.NH_NOISE_ENC,
                    MeshWireCodec.NH_FRAGMENT,
                    MeshWireCodec.NH_PING,
                    MeshWireCodec.NH_PONG,
                    -> true
                    else -> false
                }
        }
    }

    @Synchronized
    override fun onLinkFrame(frame: ByteArray) {
        if (!shouldIngest(frame)) return
        frames.addLast(frame)
        while (frames.size > 64) frames.removeFirst()
    }

    @Synchronized
    fun popAll(): List<ByteArray> {
        val out = frames.toList()
        frames.clear()
        return out
    }

    @get:Synchronized
    val size: Int get() = frames.size
}
