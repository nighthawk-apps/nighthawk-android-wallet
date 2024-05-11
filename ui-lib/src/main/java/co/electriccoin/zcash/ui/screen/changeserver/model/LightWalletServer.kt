package co.electriccoin.zcash.ui.screen.changeserver.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

const val DEFAULT_REGION = "Default"

private const val ZR_HOST = "zec.rocks" // NON-NLS
private const val ZR_HOST_NA = "na.zec.rocks" // NON-NLS
private const val ZR_HOST_SA = "sa.zec.rocks" // NON-NLS
private const val ZR_HOST_EU = "eu.zec.rocks" // NON-NLS
private const val ZR_HOST_AP = "ap.zec.rocks" // NON-NLS
private const val ZR_PORT = 443

private const val YW_HOST_1 = "lwd1.zcash-infra.com" // NON-NLS
private const val YW_HOST_2 = "lwd2.zcash-infra.com" // NON-NLS
private const val YW_HOST_3 = "lwd3.zcash-infra.com" // NON-NLS
private const val YW_HOST_4 = "lwd4.zcash-infra.com" // NON-NLS
private const val YW_HOST_5 = "lwd5.zcash-infra.com" // NON-NLS
private const val YW_HOST_6 = "lwd6.zcash-infra.com" // NON-NLS
private const val YW_HOST_7 = "lwd7.zcash-infra.com" // NON-NLS
private const val YW_HOST_8 = "lwd8.zcash-infra.com" // NON-NLS
private const val YW_PORT = 9067

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

// This regex validates server URLs with ports in format: <hostname>:<port>
// While ensuring:
// - Valid hostname format (excluding spaces and special characters)
// - Port numbers within the valid range (1-65535) and without leading zeros
// - Note that this does not cover other URL components like paths or query strings
val regex = "^(([^:/?#\\s]+)://)?([^/?#\\s]+):([1-9][0-9]{3}|[1-5][0-9]{2}|[0-9]{1,2})$".toRegex()

fun validateCustomServerValue(customServer: String): Boolean = regex.matches(customServer)
