package com.nighthawkapps.lib.android.sdk.chat.hud

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OutboundPeerSlotsTest {
    @Test
    fun parseJson_readsThreeSlotsAndUnescapesUrl() {
        val json =
            """[{"slot":0,"url":"tcp+tls://seed.example/\"q\"","state":"connected"},""" +
                """{"slot":1,"url":null,"state":"connecting"},""" +
                """{"slot":2,"url":null,"state":"sleeping"}]"""
        val slots = OutboundPeerSlots.parseJson(json)
        assertEquals(3, slots.size)
        assertEquals(0, slots[0].slot)
        assertEquals("""tcp+tls://seed.example/"q"""", slots[0].url)
        assertEquals(OutboundPeerState.CONNECTED, slots[0].state)
        assertNull(slots[1].url)
        assertEquals(OutboundPeerState.CONNECTING, slots[1].state)
        assertEquals(OutboundPeerState.SLEEPING, slots[2].state)
    }

    @Test
    fun synthesize_connectingPhaseMarksAllConnecting() {
        val slots = OutboundPeerSlots.synthesize("waiting_for_peers", daemonRunning = true)
        assertEquals(OutboundPeerSlots.HUD_SLOT_COUNT, slots.size)
        assertTrue(slots.all { it.state == OutboundPeerState.CONNECTING })
    }

    @Test
    fun synthesize_connectedMarksFirstSlotLive() {
        val slots = OutboundPeerSlots.synthesize("connected", daemonRunning = true)
        assertEquals(OutboundPeerState.CONNECTED, slots[0].state)
        assertEquals(OutboundPeerState.SLEEPING, slots[1].state)
        assertEquals("connected", slots[0].displayUrl)
    }

    @Test
    fun resolve_prefersFileJsonOverPhase() {
        val json =
            """[{"slot":0,"url":"tor://abc.onion:9601","state":"connected"},""" +
                """{"slot":1,"url":null,"state":"sleeping"},""" +
                """{"slot":2,"url":null,"state":"sleeping"}]"""
        val slots =
            OutboundPeerSlots.resolve(
                fileJson = json,
                phase = "waiting_for_peers",
                daemonRunning = true,
            )
        assertEquals("tor://abc.onion:9601", slots[0].url)
        assertEquals(OutboundPeerState.CONNECTED, slots[0].state)
    }
}
