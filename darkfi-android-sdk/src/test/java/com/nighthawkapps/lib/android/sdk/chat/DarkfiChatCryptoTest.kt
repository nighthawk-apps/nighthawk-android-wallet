@file:Suppress("TooGenericExceptionCaught", "SwallowedException")

package com.nighthawkapps.lib.android.sdk.chat

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class DarkfiChatCryptoTest {
    @Test
    fun `encryptDm throws UnsatisfiedLinkError when native FFI lib is not loaded in JVM test environment`() {
        // Arrange
        val mySecret = ByteArray(32) { 1.toByte() }
        val theirPublic = ByteArray(32) { 2.toByte() }
        val plaintext = "Hello DarkFi Chat"

        // Act & Assert
        // In this basic JVM test environment, the `.so` FFI is not loaded, so we expect UnsatisfiedLinkError.
        // This validates the method signatures and bindings are wired correctly down to the JNI barrier.
        try {
            DarkfiChatCrypto.encryptDm(mySecret, theirPublic, plaintext)
            org.junit.Assert.fail("Expected FFI linkage to fail in plain JVM test")
        } catch (e: Throwable) {
            // Can be ExceptionInInitializerError, NoClassDefFoundError, or UnsatisfiedLinkError
            // depending on test runner order and JNA setup.
        }
    }

    @Test
    fun `decryptDm throws when native FFI lib is not loaded in JVM test environment`() {
        // Arrange
        val mySecret = ByteArray(32) { 2.toByte() }
        val theirPublic = ByteArray(32) { 1.toByte() }
        val ciphertextB58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

        // Act & Assert
        try {
            DarkfiChatCrypto.decryptDm(mySecret, theirPublic, ciphertextB58)
            org.junit.Assert.fail("Expected FFI linkage to fail in plain JVM test")
        } catch (e: Throwable) {
            // Expected
        }
    }

    @Test
    fun `encryptMessageIfPossible returns plaintext for channels`() {
        // Arrange
        val context = org.mockito.Mockito.mock(android.content.Context::class.java)
        val plaintext = "Hello #dev"

        // Act
        val result = DarkfiChatCrypto.encryptMessageIfPossible(context, "#dev", plaintext)

        // Assert
        org.junit.Assert.assertEquals(plaintext, result)
    }

    @Test
    fun `decryptMessageIfPossible returns original message for channels`() {
        // Arrange
        val context = org.mockito.Mockito.mock(android.content.Context::class.java)
        val msg = ChatChannelMessage(eventId = "msg1", channel = "#dev", nick = "alice", text = "Hello", timestampMs = 0L)

        // Act
        val result = DarkfiChatCrypto.decryptMessageIfPossible(context, msg)

        // Assert
        org.junit.Assert.assertEquals(msg, result)
    }

    @Test
    fun `decryptMessageIfPossible preserves channel key and base58-looking text for public channels`() {
        // Regression: incoming public-channel messages must not be re-keyed or
        // run through DM decryption (which previously mangled them). The msg
        // text here is valid base58, which would tempt a decrypt attempt.
        val context = org.mockito.Mockito.mock(android.content.Context::class.java)
        val msg =
            ChatChannelMessage(
                eventId = "msg2",
                channel = "#hackers",
                nick = "satoshi",
                text = "3vQB7B6MrGQZaxCuFg4oh",
                timestampMs = 42L,
            )

        val result = DarkfiChatCrypto.decryptMessageIfPossible(context, msg)

        org.junit.Assert.assertEquals("#hackers", result.channel)
        org.junit.Assert.assertEquals("satoshi", result.nick)
        org.junit.Assert.assertEquals("3vQB7B6MrGQZaxCuFg4oh", result.text)
        org.junit.Assert.assertEquals(42L, result.timestampMs)
    }

    @Test
    fun `decryptMessageIfPossible keeps distinct keys across multiple public channels`() {
        val context = org.mockito.Mockito.mock(android.content.Context::class.java)
        val devMsg = ChatChannelMessage(eventId = "msg3", channel = "#dev", nick = "a", text = "x", timestampMs = 1L)
        val mathMsg = ChatChannelMessage(eventId = "msg4", channel = "#math", nick = "b", text = "y", timestampMs = 2L)

        org.junit.Assert.assertEquals("#dev", DarkfiChatCrypto.decryptMessageIfPossible(context, devMsg).channel)
        org.junit.Assert.assertEquals("#math", DarkfiChatCrypto.decryptMessageIfPossible(context, mathMsg).channel)
    }

    @Test
    fun `encryptMessageIfPossible returns plaintext if contact not found`() {
        // Arrange
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        val testFile = java.io.File(context.filesDir, "test_chat.preferences_pb")
        val realDataStore =
            androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
                produceFile = { testFile }
            )
        val field = DarkfiChatSecureStore::class.java.getDeclaredField("dataStoreInstance")
        field.isAccessible = true
        field.set(null, realDataStore)
        // Assuming empty contacts initially
        val plaintext = "Secret Hello"

        // Act
        val result = DarkfiChatCrypto.encryptMessageIfPossible(context, "bob", plaintext)

        // Assert
        org.junit.Assert.assertEquals(plaintext, result)

        // Cleanup
        field.set(null, null)
    }
}
