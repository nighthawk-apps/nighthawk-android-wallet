package com.nighthawkapps.lib.android.sdk.wallet

enum class DarkfiNetwork {
    Mainnet,
    Testnet,
    ;

    val networkName: String
        get() = name.lowercase()
}
