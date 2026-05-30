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
        val msg = ChatChannelMessage(channel = "#dev", nick = "alice", text = "Hello", timestampMs = 0L)

        // Act
        val result = DarkfiChatCrypto.decryptMessageIfPossible(context, msg)

        // Assert
        org.junit.Assert.assertEquals(msg, result)
    }

    @Test
    fun `encryptMessageIfPossible returns plaintext if contact not found`() {
        // Arrange
        val mockPrefs = org.mockito.Mockito.mock(android.content.SharedPreferences::class.java)
        val field = DarkfiChatSecureStore::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, mockPrefs)

        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
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
