package com.nighthawkapps.lib.android.sdk.chat.darkirc

import android.content.Context
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatConnectionBridge
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.generateDmKeypair as ffiGenerateDmKeypair

object DarkircCliKeygen {
    data class DmKeypair(
        val myDmChachaPublicBase58: String,
        val myDmChachaSecretBase58: String,
    )

    /** 32-byte channel ChaChaBox secret (bs58), same entropy source as a DM secret. */
    fun genChannelSecret(app: Context): String? = genChachaKeypair(app)?.myDmChachaSecretBase58

    fun genChachaKeypair(app: Context): DmKeypair? =
        runCatching {
            val kp = ffiGenerateDmKeypair()
            DmKeypair(
                myDmChachaPublicBase58 = kp.publicB58,
                myDmChachaSecretBase58 = kp.secretB58,
            )
        }.getOrNull()
}
