package co.electriccoin.zcash.ui.screen.changeserver.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.json.JSONObject

const val DEFAULT_REGION = "Default"
const val CUSTOM_SERVER = "CustomServer"
// For an existing user who is on old Nighthawk server, will change the server on upgrade if server is old one
const val OLD_NIGHTHAWK_HOST_PATTERN = ".lightwalletd.com" // Full host was mainnet.lightwalletd.com

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

sealed class LightWalletServer(
    val host: String,
    val port: Int,
    val region: String,
    val isSecure: Boolean
) {

    fun toJson() =
        JSONObject().apply {
            put(HOST, host)
            put(PORT, port)
            put(REGION, region)
            put(IS_SECURE, isSecure)
        }
    companion object {

        private const val HOST = "host"
        private const val PORT = "port"
        private const val REGION = "region"
        private const val IS_SECURE = "isSecure"
        fun from(jsonObject: JSONObject): LightWalletServer? {
            return try {
                val host = jsonObject.getString(HOST)
                val port = jsonObject.getInt(PORT)
                val region = jsonObject.getString(REGION)
                getServer(host, port, region)
            } catch (_: Exception) {
                null
            }
        }

        private fun getServer(host: String, port: Int, region: String): LightWalletServer? {
            return if (region == CUSTOM_SERVER) {
                CustomServer(host, port)
            } else if (TestnetServer.allServers().find { it.host == host } != null) {
                TestnetServer.getLightWalletServer(region)
            } else if (MainnetServer.allServers().find { it.region == region } != null) {
                MainnetServer.getLightWalletServer(region)
            } else {
                null
            }
        }
    }
}

data class CustomServer(val hostName: String, val portNo: Int):
    LightWalletServer(hostName, portNo, CUSTOM_SERVER, true)

sealed class MainnetServer(host: String, port: Int, region: String, isSecure: Boolean) :
    LightWalletServer(host, port, region, isSecure) {
    data object DEFAULT : MainnetServer(ZR_HOST, ZR_PORT, ZR_HOST, true)
    data object ZR_NA : MainnetServer(ZR_HOST_NA, ZR_PORT, ZR_HOST_NA, true)
    data object ZR_SA : MainnetServer(ZR_HOST_SA, ZR_PORT, ZR_HOST_SA, true)
    data object ZR_EU : MainnetServer(ZR_HOST_EU, ZR_PORT, ZR_HOST_EU, true)
    data object ZR_AP : MainnetServer(ZR_HOST_AP, ZR_PORT, ZR_HOST_AP, true)

    // infra
    data object YW_1 : MainnetServer(YW_HOST_1, YW_PORT, YW_HOST_1, true)
    data object YW_2 : MainnetServer(YW_HOST_2, YW_PORT, YW_HOST_2, true)
    data object YW_3 : MainnetServer(YW_HOST_3, YW_PORT, YW_HOST_3, true)
    data object YW_4 : MainnetServer(YW_HOST_4, YW_PORT, YW_HOST_4, true)
    data object YW_5 : MainnetServer(YW_HOST_5, YW_PORT, YW_HOST_5, true)
    data object YW_6 : MainnetServer(YW_HOST_6, YW_PORT, YW_HOST_6, true)
    data object YW_7 : MainnetServer(YW_HOST_7, YW_PORT, YW_HOST_7, true)
    data object YW_8 : MainnetServer(YW_HOST_8, YW_PORT, YW_HOST_8, true)

    companion object {
        fun getLightWalletServer(region: String): LightWalletServer {
            return when (region.lowercase()) {
                ZR_NA.region.lowercase() -> ZR_NA
                ZR_SA.region.lowercase() -> ZR_SA
                ZR_EU.region.lowercase() -> ZR_EU
                ZR_AP.region.lowercase() -> ZR_AP

                YW_1.region.lowercase() -> YW_1
                YW_2.region.lowercase() -> YW_2
                YW_3.region.lowercase() -> YW_3
                YW_4.region.lowercase() -> YW_4
                YW_5.region.lowercase() -> YW_5
                YW_6.region.lowercase() -> YW_6
                YW_7.region.lowercase() -> YW_7
                YW_8.region.lowercase() -> YW_8
                else -> DEFAULT
            }
        }

        fun allServers(): ImmutableList<LightWalletServer> {
            return listOf<LightWalletServer>(
                DEFAULT,
                ZR_NA,
                ZR_SA,
                ZR_EU,
                ZR_AP,
                YW_1,
                YW_2,
                YW_3,
                YW_4,
                YW_5,
                YW_6,
                YW_7,
                YW_8,
            ).toImmutableList()
        }
    }
}

sealed class TestnetServer(host: String, port: Int, region: String, isSecure: Boolean) :
    LightWalletServer(host, port, region, isSecure) {
    data object DEFAULT : TestnetServer("lightwalletd.testnet.electriccoin.co", 9067, "lightwalletd.testnet.electriccoin.co", true)

    companion object {
        fun getLightWalletServer(region: String): LightWalletServer {
            return when (region.lowercase()) {
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
