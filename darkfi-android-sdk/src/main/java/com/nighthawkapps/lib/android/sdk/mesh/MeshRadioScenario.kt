package com.nighthawkapps.lib.android.sdk.mesh

/**
 * Sans-I/O two- and three-phone radio lab. Frames are byte pipes, not a
 * live GATT stack — emulator BLE is not dual-role. Hardware two-phone
 * checks live in [MeshTwoPhoneInstrumentedTest].
 */
object MeshRadioScenario {
    enum class Step {
        Connect,
        Chat,
        DagSync,
        Lost,
        Reconnect,
        Reset,
    }

    data class Phone(
        val id: String,
        val peers: MeshPeerTable = MeshPeerTable(),
        val inbox: ArrayList<ByteArray> = ArrayList(),
        var meshOn: Boolean = true,
    )

    fun chatFrame(
        from: ByteArray,
        body: String
    ): ByteArray =
        MeshWireCodec.encode(
            MeshWirePacket(
                type = MeshWireCodec.NH_DAG_EVENT,
                ttl = 7,
                flags = 0,
                timestampMs = 0L,
                sender = from,
                recipient = null,
                payload = body.toByteArray(),
            ),
        ) ?: error("mesh frame encode")

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
        to.inbox.add(frame)
        return true
    }

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

        val chat = chatFrame(idA, "#dev ping")
        check(deliver(a, r, chat, now + 1))
        check(deliver(r, g, chat, now + 2))
        log.add("chat")

        val sync =
            MeshWireCodec.encode(
                MeshWirePacket(
                    type = MeshWireCodec.NH_DAG_SYNC,
                    ttl = 7,
                    flags = 0,
                    timestampMs = 0L,
                    sender = idA,
                    recipient = null,
                    payload = byteArrayOf(0x01, 0, 0, 0, 1, 0, 0),
                ),
            ) ?: error("mesh frame encode")
        check(deliver(a, r, sync, now + 2))
        check(deliver(r, g, sync, now + DAG_RELAY_OFFSET_MS))
        log.add("dag-sync")

        r.meshOn = false
        check(!deliver(a, r, chatFrame(idA, "lost"), now + LOST_OFFSET_MS))
        log.add("lost")

        r.meshOn = true
        r.peers.clear()
        r.peers.admit(a.id, now + LOST_OFFSET_MS)
        r.peers.admit(g.id, now + LOST_OFFSET_MS)
        check(deliver(a, r, chatFrame(idA, "rejoin"), now + REJOIN_OFFSET_MS))
        log.add("reconnect")

        r.peers.clear()
        r.peers.admit(a.id, now + RESET_ADMIT_OFFSET_MS)
        r.peers.admit(g.id, now + RESET_ADMIT_OFFSET_MS)
        val resetId = ByteArray(MeshWireCodec.SENDER_LEN) { PHONE_RESET_ID }
        check(deliver(g, r, chatFrame(resetId, "after-reset"), now + RESET_DELIVER_OFFSET_MS))
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
