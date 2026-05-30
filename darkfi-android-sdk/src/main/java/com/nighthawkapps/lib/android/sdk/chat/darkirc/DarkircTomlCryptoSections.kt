package com.nighthawkapps.lib.android.sdk.chat.darkirc

/**
 * Renders upstream-compatible `[channel."#…"]` and `[contact."…"]` blocks for embedded darkirc.
 */
internal object DarkircTomlCryptoSections {
    fun render(bundle: DarkircCryptoBundle): String {
        if (bundle.channels.isEmpty() && bundle.contacts.isEmpty()) {
            return ""
        }
        return buildString {
            bundle.channels.forEach { appendChannel(it) }
            bundle.contacts.forEach { appendContact(it) }
        }.trimEnd()
    }

    private fun StringBuilder.appendChannel(channel: DarkircChannelCryptoConfig) {
        append("\n[channel.\"")
        append(escapeTomlString(channel.channel))
        append("\"]\n")
        channel.topic?.let {
            append("topic = \"")
            append(escapeTomlString(it))
            append("\"\n")
        }
        channel.secretBase58?.let {
            append("secret = \"")
            append(escapeTomlString(it))
            append("\"\n")
        }
    }

    private fun StringBuilder.appendContact(contact: DarkircContactCryptoConfig) {
        append("\n[contact.\"")
        append(escapeTomlString(contact.nick))
        append("\"]\n")
        append("dm_chacha_public = \"")
        append(escapeTomlString(contact.dmChachaPublicBase58))
        append("\"\n")
        append("my_dm_chacha_secret = \"")
        append(escapeTomlString(contact.myDmChachaSecretBase58))
        append("\"\n")
    }

    private fun escapeTomlString(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
}
