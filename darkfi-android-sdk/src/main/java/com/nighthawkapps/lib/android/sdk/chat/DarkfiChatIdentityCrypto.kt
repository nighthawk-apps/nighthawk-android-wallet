package com.nighthawkapps.lib.android.sdk.chat

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Stable pseudonymous identifier for sharing with contacts (not the wallet seed).
 *
 * Derived from chat identity entropy; pairing semantics remain IRC/UI-layer until JNI crypto lands.
 */
internal object DarkfiChatIdentityCrypto {
    private val saltBytes = "darkfi-chat-public-id-v1".toByteArray(StandardCharsets.UTF_8)

    fun publicIdHex(entropy: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(saltBytes)
        md.update(entropy)
        return md.digest().joinToString("") { b -> "%02x".format(b) }
    }
}
