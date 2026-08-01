package com.nighthawkapps.lib.android.ui.fixture

import com.nighthawkapps.lib.android.sdk.wallet.DarkfiEndpoint
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiNetwork
import com.nighthawkapps.lib.android.sdk.wallet.PersistableDarkfiWallet

object PersistableDarkfiWalletFixture {
    /** Standard BIP-39 English test vector (256-bit entropy + checksum). */
    fun validMnemonicWords(): List<String> =
        (
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art"
        ).split(" ")

    /** Preview/tests — deterministic valid mnemonic; do not use for real wallets. */
    fun new(): PersistableDarkfiWallet =
        PersistableDarkfiWallet(
            seedPhrase = validMnemonicWords(),
            network = DarkfiNetwork.Testnet,
            endpoint = DarkfiEndpoint.defaultForNetwork(DarkfiNetwork.Testnet),
            birthdayHeight = null,
        )
}
