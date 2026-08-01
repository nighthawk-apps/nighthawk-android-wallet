package com.nighthawkapps.lib.android.sdk.chat.dm

import kotlinx.serialization.Serializable

/** UI metadata for a configured DM contact (keys live in [DarkircCryptoStore]). */
@Serializable
data class DmConversationMeta(
    val contactLabel: String,
    val peerPublicKeyBase58: String? = null,
    val lastMessagePreview: String? = null,
    val lastMessageAtMs: Long = 0L,
)
