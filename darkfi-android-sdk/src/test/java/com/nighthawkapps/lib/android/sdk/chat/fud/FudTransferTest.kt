package com.nighthawkapps.lib.android.sdk.chat.fud

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FudTransferTest {
    @Test
    fun parseValidUri() {
        val uri = FudUri.parse("fud://abcdef012345/file.png")
        assertNotNull(uri)
        assertEquals("abcdef012345", uri.infoHash)
        assertEquals("file.png", uri.fileName)
    }

    @Test
    fun parseRejectsShortHashAndTraversal() {
        assertNull(FudUri.parse("fud://ab/file.png"))
        assertNull(FudUri.parse("fud://abcdef01/../secret"))
        assertNull(FudUri.parse("https://example.com/x"))
        assertNull(FudUri.parse("fud://"))
    }

    @Test
    fun policyRequiresToggleAndPrivateTransport() {
        val uri = FudUri.parse("fud://abcdef012345/a.bin")
        assertEquals(
            FudTransferDecision.Disabled,
            FudTransferPolicy.decide(enabled = false, torReady = true, meshOn = false, uri = uri),
        )
        assertEquals(
            FudTransferDecision.NeedsPrivateTransport,
            FudTransferPolicy.decide(enabled = true, torReady = false, meshOn = false, uri = uri),
        )
        assertEquals(
            FudTransferDecision.Allowed,
            FudTransferPolicy.decide(enabled = true, torReady = true, meshOn = false, uri = uri),
        )
        assertEquals(
            FudTransferDecision.Allowed,
            FudTransferPolicy.decide(enabled = true, torReady = false, meshOn = true, uri = uri),
        )
        assertEquals(
            FudTransferDecision.Invalid,
            FudTransferPolicy.decide(enabled = true, torReady = true, meshOn = true, uri = null),
        )
    }

    @Test
    fun inboxQueuesWithoutFetching() {
        val dir = File.createTempFile("fud", "dir").apply {
            delete()
            mkdirs()
        }
        val uri = FudUri.parse("fud://abcdef012345/notes.txt")!!
        val file = FudOfferInbox.queue(dir, uri, nowMs = 42L)
        assertTrue(file.exists())
        val body = file.readText()
        assertTrue(body.contains("abcdef012345"))
        assertTrue(body.contains("notes.txt"))
        assertFalse(body.contains("http://"))
        dir.deleteRecursively()
    }
}
