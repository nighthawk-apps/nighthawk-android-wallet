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

    fun chatFrame(from: ByteArray, body: String): ByteArray =
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
        if (!from.meshOn || !to.meshOn) return false
        to.peers.admit(from.id, nowMs)
        val pkt = MeshWireCodec.decode(frame) ?: return false
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
        val idA = ByteArray(8) { 0x0A }
        val now = 1_000L
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
        check(deliver(r, g, sync, now + 3))
        log.add("dag-sync")

        r.meshOn = false
        check(!deliver(a, r, chatFrame(idA, "lost"), now + 4))
        log.add("lost")

        r.meshOn = true
        r.peers.clear()
        r.peers.admit(a.id, now + 4)
        r.peers.admit(g.id, now + 4)
        check(deliver(a, r, chatFrame(idA, "rejoin"), now + 5))
        log.add("reconnect")

        r.peers.clear()
        r.peers.admit(a.id, now + 6)
        r.peers.admit(g.id, now + 6)
        check(deliver(g, r, chatFrame(ByteArray(8) { 0x0C }, "after-reset"), now + 7))
        log.add("reset")
        return log
    }
}
