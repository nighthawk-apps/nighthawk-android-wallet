package co.electriccoin.zcash.ui.screen.changeserver.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList


const val DEFAULT_REGION = "Default"
sealed class LightWalletServer(val host: String, val port: Int, val region: String, val isSecure: Boolean)
sealed class MainnetServer(host: String, port: Int, region: String, isSecure: Boolean) :
    LightWalletServer(host, port, region, isSecure) {
    data object DEFAULT : MainnetServer("mainnet.lightwalletd.com", 9067, DEFAULT_REGION, true)
    data object ASIA_OCEANIA : MainnetServer("ai.lightwalletd.com", 443, "Asia & Oceania", true)
    data object EUROPE_AFRICA : MainnetServer("eu.lightwalletd.com", 443, "Europe & Africa", true)
    data object NORTH_AMERICA : MainnetServer("na.lightwalletd.com", 443, "North America", true)
    data object SOUTH_AMERICA : MainnetServer("sa.lightwalletd.com", 443, "South America", true)

    companion object {
        fun getLightWalletServer(region: String): LightWalletServer {
            return when (region.lowercase()) {
                ASIA_OCEANIA.region.lowercase() -> ASIA_OCEANIA
                EUROPE_AFRICA.region.lowercase() -> EUROPE_AFRICA
                NORTH_AMERICA.region.lowercase() -> NORTH_AMERICA
                SOUTH_AMERICA.region.lowercase() -> SOUTH_AMERICA
                else -> DEFAULT
            }
        }

        fun allServers(): ImmutableList<LightWalletServer> {
            return listOf<LightWalletServer>(
                DEFAULT,
                ASIA_OCEANIA,
                EUROPE_AFRICA,
                NORTH_AMERICA,
                SOUTH_AMERICA
            ).toImmutableList()
        }
    }
}

sealed class TestnetServer(host: String, port: Int, region: String, isSecure: Boolean) :
    LightWalletServer(host, port, region, isSecure) {
    data object DEFAULT : TestnetServer("testnet.lightwalletd.com", 9067, DEFAULT_REGION, true)

    companion object {
        fun getLightWalletServer(region: String): LightWalletServer {
            return when(region.lowercase()) {
                DEFAULT.region.lowercase() -> DEFAULT
                else -> DEFAULT
            }
        }
        fun allServers(): ImmutableList<LightWalletServer> {
            return listOf<LightWalletServer>(
                DEFAULT
            ).toImmutableList()
        }
    }
}
