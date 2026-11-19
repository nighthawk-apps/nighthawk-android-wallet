package com.nighthawkapps.lib.android.sdk.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatWalletTest {
    @Test
    fun invoice_parsesAmountMemoAndToken() {
        val raw = "pay drk:drk1abc?amount=1.5&memo=bHVuY2g&token=DRK thanks"
        val invoice = ChatInvoices.find(raw)
        assertNotNull(invoice)
        assertEquals("drk1abc", invoice.address)
        assertEquals("1.5", invoice.amount)
        assertEquals("lunch", invoice.memo)
        assertEquals("DRK", invoice.token)
    }

    @Test
    fun receipt_roundTripsPhase() {
        val encoded =
            ChatReceiptCodec.encode(
                ChatReceipt(
                    id = "r1",
                    phase = ChatReceiptPhase.Pending,
                    amount = "0.5",
                    address = "drk1abc",
                    memo = "tip",
                    txId = null,
                ),
            )
        val parsed = ChatReceiptCodec.parse(encoded)
        assertNotNull(parsed)
        assertEquals(ChatReceiptPhase.Pending, parsed.phase)
        assertEquals("tip", parsed.memo)
        assertNull(parsed.txId)
    }

    @Test
    fun notification_skipsSelfEchoAndReplay() {
        val now = 1_000_000L
        assertFalse(
            ChatNotificationPolicy.shouldAlert(
                isOutgoing = true,
                ownNick = "hawk",
                nick = "hawk",
                timestampMs = now,
                nowMs = now,
            ),
        )
        assertFalse(
            ChatNotificationPolicy.shouldAlert(
                isOutgoing = false,
                ownNick = "hawk",
                nick = "Hawk",
                timestampMs = now,
                nowMs = now,
            ),
        )
        assertFalse(
            ChatNotificationPolicy.shouldAlert(
                isOutgoing = false,
                ownNick = "hawk",
                nick = "alice",
                timestampMs = now - ChatNotificationPolicy.FRESH_MS - 1,
                nowMs = now,
            ),
        )
        assertTrue(
            ChatNotificationPolicy.shouldAlert(
                isOutgoing = false,
                ownNick = "hawk",
                nick = "alice",
                timestampMs = now - 1_000,
                nowMs = now,
            ),
        )
    }

    @Test
    fun linkGuard_onlyClearnetHttp() {
        assertTrue(ChatLinkGuard.isClearnetHttp("https://dark.fi/docs"))
        assertTrue(ChatLinkGuard.isClearnetHttp("http://example.com"))
        assertFalse(ChatLinkGuard.isClearnetHttp("fud://abc"))
    }

    @Test
    fun invite_roundTripAndSafetyNumberStable() {
        val contact =
            NighthawkContact(
                id = "alice",
                nickname = "alice",
                dmPublicKey = "pubkey",
                walletAddress = "drk1abc",
            )
        val uri = NighthawkContacts.inviteUri(contact)
        assertNotNull(uri)
        assertEquals("drk:drk1abc", uri)
        val parsed = NighthawkContacts.parseInvite(uri!!)
        assertNotNull(parsed)
        assertEquals("drk1abc", parsed!!.walletAddress)
        assertNull(NighthawkContacts.inviteUri(contact.copy(walletAddress = null)))
        assertEquals(
            NighthawkContacts.safetyNumber("pubkey", "drk1abc"),
            NighthawkContacts.safetyNumber("pubkey", "drk1abc"),
        )
    }

    @Test
    fun delivery_prefixes() {
        assertEquals(ChatDeliveryState.Queued, ChatDeliveryLabel.from("queued-1", true))
        assertEquals(ChatDeliveryState.Sending, ChatDeliveryLabel.from("local-1", true))
        assertEquals(ChatDeliveryState.Failed, ChatDeliveryLabel.from("failed-1", true))
        assertEquals(ChatDeliveryState.Relayed, ChatDeliveryLabel.from("evt", true))
        assertNull(ChatDeliveryLabel.from("evt", false))
    }
}
