package com.nighthawkapps.lib.android.sdk.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class PendingOutgoingChatRecord(
    @SerialName("id") val id: String = UUID.randomUUID().toString(),
    /** Normalized `#channel`. */
    @SerialName("ch") val channelNormalized: String,
    @SerialName("t") val textSanitized: String,
    @SerialName("q") val queuedAtEpochMs: Long,
)

@Serializable
data class PendingOutgoingChatFile(
    @SerialName("v") val version: Int = 1,
    @SerialName("items") val items: List<PendingOutgoingChatRecord> = emptyList(),
)
