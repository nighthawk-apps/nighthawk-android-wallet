package com.nighthawkapps.lib.android.ui.security

import android.content.Context
import androidx.datastore.core.Serializer
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import okio.buffer
import okio.sink
import okio.source
import java.io.InputStream
import java.io.OutputStream

internal class SecureDataStoreSerializer(
    private val aead: Aead
) : Serializer<Preferences> {
    override val defaultValue: Preferences
        get() = PreferencesSerializer.defaultValue

    override suspend fun readFrom(input: InputStream): Preferences {
        return try {
            val encryptedBytes = input.readBytes()
            if (encryptedBytes.isEmpty()) return defaultValue
            val decryptedBytes = aead.decrypt(encryptedBytes, null)
            PreferencesSerializer.readFrom(decryptedBytes.inputStream().source().buffer())
        } catch (e: Exception) {
            e.printStackTrace()
            defaultValue
        }
    }

    override suspend fun writeTo(
        t: Preferences,
        output: OutputStream
    ) {
        val byteStream = java.io.ByteArrayOutputStream()
        PreferencesSerializer.writeTo(t, byteStream.sink().buffer())
        val encryptedBytes = aead.encrypt(byteStream.toByteArray(), null)
        output.write(encryptedBytes)
    }

    companion object {
        fun createAead(
            context: Context,
            keysetName: String
        ): Aead {
            AeadConfig.register()
            return AndroidKeysetManager
                .Builder()
                .withSharedPref(context, keysetName, "secure_datastore_keyset")
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .withMasterKeyUri("android-keystore://master_key_$keysetName")
                .build()
                .keysetHandle
                .getPrimitive(Aead::class.java)
        }
    }
}
