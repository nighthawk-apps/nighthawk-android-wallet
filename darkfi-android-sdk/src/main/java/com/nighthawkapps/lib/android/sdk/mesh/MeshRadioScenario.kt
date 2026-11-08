package com.nighthawkapps.lib.android.sdk.mesh

/**
 * Sans-I/O two- and three-phone **radio / OS policy** lab.
 *
 * Frames move through byte pipes (not a live GATT stack). Emulator BLE is not
 * dual-role, so this is the Android-side virtual multi-phone path. Crypto
 * handshake, reseal, and EventPut delivery live in Rust
 * `darkfi-mobile-ffi` `three_device_*` mesh tests (`hop_star`).
 *
 * Ingest policy matches production [MeshFrameInbox]: only Noise HS/ENC,
 * Fragment, Ping, and Pong are admitted. Plaintext `NH_DAG_EVENT` /
 * `NH_DAG_SYNC` are dropped (same as air).
 *
 * Hardware two-phone GATT checks: [MeshTwoPhoneInstrumentedTest].
 */
object MeshRadioScenario {
    data class Phone(
        val id: String,
        val peers: MeshPeerTable = MeshPeerTable(),
        val inbox: ArrayList<ByteArray> = ArrayList(),
        var meshOn: Boolean = true,
        val frameInbox: MeshFrameInbox = MeshFrameInbox(),
    )

    /** Sealed EventPut stand-in (Noise ENC) — what the OS layer may ingest. */
    fun sealedChatFrame(
        from: ByteArray,
        payload: ByteArray,
    ): ByteArray =
        MeshWireCodec.encode(
            MeshWirePacket(
                type = MeshWireCodec.NH_NOISE_ENC,
                ttl = 7,
                flags = 0,
                timestampMs = 0L,
                sender = from,
                recipient = null,
                payload = payload,
            ),
        ) ?: error("mesh frame encode")

    fun pingFrame(from: ByteArray): ByteArray =
        MeshWireCodec.encode(
            MeshWirePacket(
                type = MeshWireCodec.NH_PING,
                ttl = 0,
                flags = 0,
                timestampMs = 0L,
                sender = from,
                recipient = null,
                payload = ByteArray(0),
            ),
        ) ?: error("mesh frame encode")

    /** Legacy helper name used by unit tests for a sealed chat-shaped frame. */
    fun chatFrame(
        from: ByteArray,
        body: String,
    ): ByteArray = sealedChatFrame(from, body.toByteArray())

    fun deliver(
        from: Phone,
        to: Phone,
        frame: ByteArray,
        nowMs: Long,
    ): Boolean {
        if (!radiosUp(from, to)) return false
        return deliverFrame(from, to, frame, nowMs)
    }

    private fun radiosUp(
        from: Phone,
        to: Phone,
    ): Boolean = from.meshOn && to.meshOn

    private fun deliverFrame(
        from: Phone,
        to: Phone,
        frame: ByteArray,
        nowMs: Long,
    ): Boolean {
        to.peers.admit(from.id, nowMs)
        val pkt = MeshWireCodec.decode(frame) ?: return false
        return acceptFrame(to, frame, pkt)
    }

    private fun acceptFrame(
        to: Phone,
        frame: ByteArray,
        pkt: MeshWirePacket,
    ): Boolean {
        if (pkt.type == MeshWireCodec.NH_LWD_CTRL && !MeshLwdAllowlist.isAllowed(pkt.payload)) {
            return false
        }
        // Match NighthawkMeshService: drop plaintext DAG / sync / LWD before native.
        if (!to.frameInbox.shouldIngest(frame)) {
            return false
        }
        to.inbox.add(frame)
        return true
    }

    /**
     * Star topology A—R—G: connect, sealed chat hop, ping, link loss,
     * reconnect, relay peer-table reset.
     */
    fun runThreePhoneScript(): List<String> {
        val log = ArrayList<String>()
        val a = Phone("a")
        val r = Phone("r")
        val g = Phone("g")
        val idA = ByteArray(MeshWireCodec.SENDER_LEN) { PHONE_A_ID }
        val now = SCRIPT_EPOCH_MS
        r.peers.admit(a.id, now)
        r.peers.admit(g.id, now)
        a.peers.admit(r.id, now)
        g.peers.admit(r.id, now)
        log.add("connect a-r-g")

        val chat = sealedChatFrame(idA, "#dev ping".toByteArray())
        check(deliver(a, r, chat, now + 1))
        check(deliver(r, g, chat, now + 2))
        log.add("chat")

        val syncPing = pingFrame(idA)
        check(deliver(a, r, syncPing, now + 2))
        check(deliver(r, g, syncPing, now + DAG_RELAY_OFFSET_MS))
        log.add("dag-sync")

        r.meshOn = false
        check(!deliver(a, r, sealedChatFrame(idA, "lost".toByteArray()), now + LOST_OFFSET_MS))
        log.add("lost")

        r.meshOn = true
        r.peers.clear()
        r.peers.admit(a.id, now + LOST_OFFSET_MS)
        r.peers.admit(g.id, now + LOST_OFFSET_MS)
        check(deliver(a, r, sealedChatFrame(idA, "rejoin".toByteArray()), now + REJOIN_OFFSET_MS))
        log.add("reconnect")

        r.peers.clear()
        r.peers.admit(a.id, now + RESET_ADMIT_OFFSET_MS)
        r.peers.admit(g.id, now + RESET_ADMIT_OFFSET_MS)
        val resetId = ByteArray(MeshWireCodec.SENDER_LEN) { PHONE_RESET_ID }
        check(
            deliver(
                g,
                r,
                sealedChatFrame(resetId, "after-reset".toByteArray()),
                now + RESET_DELIVER_OFFSET_MS,
            ),
        )
        log.add("reset")
        return log
    }

    private const val PHONE_A_ID: Byte = 0x0A
    private const val PHONE_RESET_ID: Byte = 0x0C
    private const val SCRIPT_EPOCH_MS: Long = 1_000L
    private const val DAG_RELAY_OFFSET_MS: Long = 3L
    private const val LOST_OFFSET_MS: Long = 4L
    private const val REJOIN_OFFSET_MS: Long = 5L
    private const val RESET_ADMIT_OFFSET_MS: Long = 6L
    private const val RESET_DELIVER_OFFSET_MS: Long = 7L
}
