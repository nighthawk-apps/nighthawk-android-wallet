package com.nighthawkapps.lib.android.sdk.chat.darkirc

import kotlinx.serialization.Serializable

/** Upstream `[channel."#name"]` encryption block (ChaChaBox shared secret). */
@Serializable
data class DarkircChannelCryptoConfig(
    val channel: String,
    val secretBase58: String? = null,
    val topic: String? = null,
) {
    init {
        require(channel.startsWith("#")) { "channel must start with #" }
        secretBase58?.let { require(DarkircBs58.isValidSecret32(it)) { "invalid channel secret" } }
    }
}

/** Upstream `[contact."nick"]` DM encryption (pairwise ChaChaBox keys). */
@Serializable
data class DarkircContactCryptoConfig(
    val nick: String,
    val dmChachaPublicBase58: String,
    val myDmChachaSecretBase58: String,
) {
    init {
        require(nick.isNotBlank()) { "contact nick required" }
        require(DarkircBs58.isValidSecret32(dmChachaPublicBase58)) { "invalid dm_chacha_public" }
        require(DarkircBs58.isValidSecret32(myDmChachaSecretBase58)) { "invalid my_dm_chacha_secret" }
    }
}

/** Full crypto overlay merged into generated `darkirc_config.toml`. */
@Serializable
data class DarkircCryptoBundle(
    val channels: List<DarkircChannelCryptoConfig> = emptyList(),
    val contacts: List<DarkircContactCryptoConfig> = emptyList(),
)
