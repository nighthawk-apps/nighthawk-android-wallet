/*
 * Embedded Tor file layout and asset keys (Guardian tor-android + bundled torrc/geoip).
 */
package com.nighthawkapps.lib.android.sdk.tor.embedded

internal object EmbeddedTorConstants {
    const val DIRECTORY_TOR_DATA = "data"
    const val TOR_CONTROL_PORT_FILE = "control.txt"
    const val SHELL_CMD_PS = "ps"
    const val GEOIP_ASSET_KEY = "geoip"
    const val GEOIP6_ASSET_KEY = "geoip6"
    const val TOR_ASSET_KEY = "libtor"
    const val TORRC_ASSET_KEY = "torrc"
    const val COMMON_ASSET_KEY = "common/"
    const val TOR_CONTROL_COOKIE = "control_auth_cookie"
    const val IP_LOCALHOST = "127.0.0.1"
    const val TOR_TRANSPROXY_PORT_DEFAULT = "9040"
    const val TOR_DNS_PORT_DEFAULT = "5400"
    const val HTTP_PROXY_PORT_DEFAULT = "8118"
    const val SOCKS_PROXY_PORT_DEFAULT = "9050"
    var FILE_WRITE_BUFFER_SIZE = 1024
}
