package com.nighthawkapps.lib.android.sdk.wallet

/**
 * Canonical DarkFi block-explorer URLs. Only `/tx/{id}` is published;
 * there is no `/address/` route — do not invent one.
 */
object DarkfiExplorer {
    const val MAINNET_BASE: String = "https://explorer.dark.fi"
    const val TESTNET_BASE: String = "https://explorer.testnet.dark.fi"

    fun baseUrl(network: DarkfiNetwork?): String =
        if (network == DarkfiNetwork.Mainnet) MAINNET_BASE else TESTNET_BASE

    fun transactionUrl(
        txid: String,
        network: DarkfiNetwork?,
    ): String? {
        val id = sanitizedPathComponent(txid) ?: return null
        return "${baseUrl(network)}/tx/$id"
    }

    fun sanitizedPathComponent(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.any { it == '/' || it == '?' || it == '#' || it == '\\' }) return null
        return trimmed
    }
}
