package com.nighthawkapps.lib.android.sdk.wallet

sealed class WalletInitMode {
    data object NewWallet : WalletInitMode()

    data class ImportWallet(
        val seedPhraseWords: List<String>
    ) : WalletInitMode()
}
