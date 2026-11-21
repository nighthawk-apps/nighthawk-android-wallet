package com.nighthawkapps.lib.android.sdk.chat

import android.app.Application
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ChatHistoryStoreTest {
    private val context: Application
        get() = RuntimeEnvironment.getApplication() as Application

    @Before
    fun setUp() {
        AeadConfig.register()
        val aead =
            KeysetHandle
                .generateNew(KeyTemplates.get("AES256_GCM"))
                .getPrimitive(Aead::class.java)
        ChatHistoryStore.aeadOverride = aead
        File(context.filesDir, "chat_history_v1.enc").delete()
        File(context.filesDir, "chat_history_v1.json").delete()
    }

    @After
    fun tearDown() {
        ChatHistoryStore.aeadOverride = null
        File(context.filesDir, "chat_history_v1.enc").delete()
        File(context.filesDir, "chat_history_v1.json").delete()
    }

    @Test
    fun appendAndLoad_encryptsOnDisk_andNeverWritesPlaintextJson() {
        val secretText = "ultra_secret_dm_payload_987654"
        ChatHistoryStore.append(
            context,
            ChatChannelMessage(
                eventId = "evt-1",
                channel = "alice",
                nick = "bob",
                text = secretText,
                timestampMs = 1_000L,
                isOutgoing = false,
            ),
        )

        val encFile = File(context.filesDir, "chat_history_v1.enc")
        val plainFile = File(context.filesDir, "chat_history_v1.json")
        assertTrue(encFile.exists())
        assertFalse(plainFile.exists())
        val rawCiphertext = encFile.readBytes().decodeToString()
        assertFalse(rawCiphertext.contains(secretText))

        val loaded = ChatHistoryStore.load(context)
        assertEquals(1, loaded["alice"]?.size)
        assertEquals(secretText, loaded["alice"]?.single()?.text)
    }

    @Test
    fun load_migratesAndDeletesLegacyPlaintextJson() {
        val legacyFile = File(context.filesDir, "chat_history_v1.json")
        legacyFile.writeText(
            """{"lines":[{"eventId":"old-1","channel":"#dev","nick":"alice",""" +
                """"text":"legacy message","timestampMs":42,"isOutgoing":false}]}""",
        )

        val loaded = ChatHistoryStore.load(context)
        assertEquals(1, loaded["#dev"]?.size)
        assertEquals("legacy message", loaded["#dev"]?.single()?.text)
        assertFalse(legacyFile.exists())
        assertTrue(File(context.filesDir, "chat_history_v1.enc").exists())
    }
}
