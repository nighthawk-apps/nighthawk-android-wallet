@file:Suppress("ktlint:standard:filename")

package cash.z.ecc.sdk.extension

import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint

/*
 * This set of extension functions suit for default values for the SDK initialization.
 */

fun LightWalletEndpoint.Companion.defaultForNetwork(zcashNetwork: ZcashNetwork): LightWalletEndpoint {
    return when (zcashNetwork.id) {
        ZcashNetwork.Mainnet.id -> LightWalletEndpoint.Mainnet
        ZcashNetwork.Testnet.id -> LightWalletEndpoint.Testnet
        else -> error("Unknown network id: ${zcashNetwork.id}")
    }
}

private const val DEFAULT_PORT = 9067

val LightWalletEndpoint.Companion.Mainnet
    get() =
        LightWalletEndpoint(
            "zec.rocks",
            443,
            isSecure = true
        )

val LightWalletEndpoint.Companion.Testnet
    get() =
        LightWalletEndpoint(
            "lightwalletd.testnet.electriccoin.co",
            9067,
            isSecure = true
        )

const val MIN_PORT_NUMBER = 1
const val MAX_PORT_NUMBER = 65535

fun LightWalletEndpoint.isValid() = host.isNotBlank() && port in MIN_PORT_NUMBER..MAX_PORT_NUMBER
