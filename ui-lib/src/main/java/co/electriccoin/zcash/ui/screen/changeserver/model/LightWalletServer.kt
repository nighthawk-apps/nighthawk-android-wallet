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

    companion object {
        fun getLightWalletServer(region: String): LightWalletServer {
            return when (region.lowercase()) {
                ZR_NA.region.lowercase() -> ZR_NA
                ZR_SA.region.lowercase() -> ZR_SA
                ZR_EU.region.lowercase() -> ZR_EU
                ZR_AP.region.lowercase() -> ZR_AP

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
