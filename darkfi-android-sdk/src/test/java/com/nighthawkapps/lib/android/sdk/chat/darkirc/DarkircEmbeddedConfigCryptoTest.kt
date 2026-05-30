package com.nighthawkapps.lib.android.sdk.chat.darkirc

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DarkircEmbeddedConfigCryptoTest {
    @Test
    fun buildToml_includesChannelAndContactSections() {
        val root =
            File.createTempFile("darkirc-root", null).apply {
                delete()
                mkdirs()
            }
        val secret = "11111111111111111111111111111111"
        val crypto =
            DarkircCryptoBundle(
                channels =
                    listOf(
                        DarkircChannelCryptoConfig(
                            channel = "#dev",
                            secretBase58 = secret,
                            topic = "dev chat",
                        ),
                    ),
                contacts =
                    listOf(
                        DarkircContactCryptoConfig(
                            nick = "alice",
                            dmChachaPublicBase58 = secret,
                            myDmChachaSecretBase58 = secret,
                        ),
                    ),
            )
        val toml =
            DarkircEmbeddedConfigGenerator.buildToml(
                root,
                crypto = crypto,
            )
        assertTrue(toml.contains("[channel.\"#dev\"]"))
        assertTrue(toml.contains("secret = \"$secret\""))
        assertTrue(toml.contains("topic = \"dev chat\""))
        assertTrue(toml.contains("[contact.\"alice\"]"))
        assertTrue(toml.contains("dm_chacha_public = \"$secret\""))
        assertTrue(toml.contains("my_dm_chacha_secret = \"$secret\""))
    }

    @Test
    fun buildToml_omitsCryptoWhenEmpty() {
        val root =
            File.createTempFile("darkirc-root2", null).apply {
                delete()
                mkdirs()
            }
        val toml = DarkircEmbeddedConfigGenerator.buildToml(root)
        assertFalse(toml.contains("[channel."))
        assertFalse(toml.contains("[contact."))
    }
}
