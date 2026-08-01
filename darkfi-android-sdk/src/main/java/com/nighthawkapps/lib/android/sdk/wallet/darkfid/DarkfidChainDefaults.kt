@file:Suppress("MagicNumber")

package com.nighthawkapps.lib.android.sdk.wallet.darkfid

import com.nighthawkapps.lib.android.sdk.wallet.DarkfiEndpoint
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiNetwork

/**
 * P2P / RPC defaults from upstream `bin/darkfid/darkfid_config.toml` (testnet section).
 */
object DarkfidChainDefaults {
    val TESTNET_MAGIC_BYTES: IntArray = intArrayOf(163, 139, 113, 101)
    val MAINNET_MAGIC_BYTES: IntArray = intArrayOf(101, 79, 61, 43)

    val TESTNET_CLEARNET_P2P_SEEDS: List<String> =
        listOf(
            "tcp+tls://lilith0.dark.fi:18340",
            "tcp+tls://lilith1.dark.fi:18340",
        )

    val MAINNET_CLEARNET_P2P_SEEDS: List<String> =
        listOf(
            "tcp+tls://lilith0.dark.fi:8340",
            "tcp+tls://lilith1.dark.fi:8340",
        )

    /** @deprecated use [TESTNET_CLEARNET_P2P_SEEDS] */
    val CLEARNET_P2P_SEEDS: List<String> = TESTNET_CLEARNET_P2P_SEEDS

    /** Testnet Tor profile seeds (`darkfid_config.toml` → testnet `net.profiles."tor"`). */
    val TESTNET_TOR_ONION_P2P_SEEDS: List<String> =
        listOf(
            "tor://wgxxaifz5gv4iggcflyl67lgmsihffs6bbwobqah4np52t3y3olrnpid.onion:18341",
            "tor://inx5s3pdzddvgb5ii3oydutmbvw6fvor3oqu65wtxl3pyevtvrdn4had.onion:18341",
        )

    /**
     * Mainnet has no published onion seeds in upstream `darkfid_config.toml` today; use
     * `tor+tls://lilith*.dark.fi:8340` over SOCKS (see `MAINNET_TOR_TLS_P2P_SEEDS`).
     */
    val MAINNET_TOR_ONION_P2P_SEEDS: List<String> = emptyList()

    /** `tor+tls` lilith seeds for SOCKS profile on mainnet (`darkfid_config.toml`). */
    val MAINNET_TOR_TLS_P2P_SEEDS: List<String> =
        listOf(
            "tor+tls://lilith0.dark.fi:8340",
            "tor+tls://lilith1.dark.fi:8340",
        )

    /** @deprecated use [torOnionP2pSeeds] */
    val TOR_ONION_P2P_SEEDS: List<String> = TESTNET_TOR_ONION_P2P_SEEDS

    fun jsonRpcPort(network: DarkfiNetwork): Int =
        when (network) {
            DarkfiNetwork.Mainnet -> DarkfiEndpoint.DARKFID_JSON_RPC_PORT_MAINNET
            DarkfiNetwork.Testnet -> DarkfiEndpoint.DARKFID_JSON_RPC_PORT_TESTNET
        }

    fun loopbackJsonRpcUri(network: DarkfiNetwork): String = "tcp://127.0.0.1:${jsonRpcPort(network)}"

    fun clearnetP2pSeeds(network: DarkfiNetwork): List<String> =
        when (network) {
            DarkfiNetwork.Mainnet -> MAINNET_CLEARNET_P2P_SEEDS
            DarkfiNetwork.Testnet -> TESTNET_CLEARNET_P2P_SEEDS
        }

    fun magicBytes(network: DarkfiNetwork): IntArray =
        when (network) {
            DarkfiNetwork.Mainnet -> MAINNET_MAGIC_BYTES
            DarkfiNetwork.Testnet -> TESTNET_MAGIC_BYTES
        }

    fun managementRpcPort(network: DarkfiNetwork): Int =
        when (network) {
            DarkfiNetwork.Mainnet -> DarkfiEndpoint.DARKFID_MANAGEMENT_RPC_PORT_MAINNET
            DarkfiNetwork.Testnet -> DarkfiEndpoint.DARKFID_MANAGEMENT_RPC_PORT_TESTNET
        }

    /** Upstream `darkfid_config.toml` confirmation thresholds. */
    fun confirmationThreshold(network: DarkfiNetwork): Int =
        when (network) {
            DarkfiNetwork.Mainnet -> 11
            DarkfiNetwork.Testnet -> 6
        }

    fun torOnionP2pSeeds(network: DarkfiNetwork): List<String> =
        when (network) {
            DarkfiNetwork.Mainnet -> MAINNET_TOR_ONION_P2P_SEEDS
            DarkfiNetwork.Testnet -> TESTNET_TOR_ONION_P2P_SEEDS
        }

    /** SOCKS-wrapped clearnet/Tor+TLS seeds when onion hosts are not published for a network. */
    fun torTlsP2pSeeds(network: DarkfiNetwork): List<String> =
        when (network) {
            DarkfiNetwork.Mainnet -> {
                MAINNET_TOR_TLS_P2P_SEEDS
            }

            DarkfiNetwork.Testnet -> {
                listOf(
                    "tor+tls://lilith0.dark.fi:18340",
                    "tor+tls://lilith1.dark.fi:18340",
                )
            }
        }
}
