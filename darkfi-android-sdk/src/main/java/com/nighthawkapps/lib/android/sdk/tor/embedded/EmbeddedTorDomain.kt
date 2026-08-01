@file:Suppress("MaxLineLength")

package com.nighthawkapps.lib.android.sdk.tor.embedded

import android.app.Application
import android.content.Context
import java.io.File

internal enum class EmbeddedEntityStatus(
    val processId: Int,
) {
    STARTING(-1),
    RUNNING(1),
    STOPPED(0),
    ;

    companion object {
        fun getByProcessId(procId: Int): EmbeddedEntityStatus = entries.find { it.processId == procId } ?: RUNNING
    }
}

internal enum class EmbeddedConnectionStatus {
    CLOSED,
    CONNECTING,
    CONNECTED,
    FAILED,
    ;

    companion object {
        fun getByName(typName: String): EmbeddedConnectionStatus = entries.find { it.name == typName.uppercase() } ?: CLOSED
    }
}

/** Mutable Tor session state for control protocol + UI. */
internal object EmbeddedTorDomain {
    class Info(
        var connection: Connection,
    ) {
        var processId: Int
            get() = connection.processId
            set(value) {
                connection.processId = value
            }

        var isInstalled: Boolean = false
        var statusMessage: String? = null

        var status: EmbeddedEntityStatus
            get() = EmbeddedEntityStatus.getByProcessId(processId)
            set(value) {
                processId = value.processId
                if (value == EmbeddedEntityStatus.STOPPED) {
                    connection.status = EmbeddedConnectionStatus.CLOSED
                }
            }
    }

    class Connection(
        processIdArg: Int = -1,
    ) {
        var processId: Int = processIdArg
            set(value) {
                if (field > 0) {
                    status = EmbeddedConnectionStatus.CONNECTING
                }
                field = value
            }

        var proxyHost = EmbeddedTorConstants.IP_LOCALHOST
        var proxySocksPort = EmbeddedTorConstants.SOCKS_PROXY_PORT_DEFAULT
        var proxyHttpPort = EmbeddedTorConstants.HTTP_PROXY_PORT_DEFAULT
        var status: EmbeddedConnectionStatus = EmbeddedConnectionStatus.CLOSED
    }

    class Settings(
        var context: Context,
    ) {
        var appFilesDir: File = context.filesDir
        var appDataDir: File = context.getDir(EmbeddedTorConstants.DIRECTORY_TOR_DATA, Application.MODE_PRIVATE)
        var appNativeDir: File = File(context.applicationInfo.nativeLibraryDir)
        var appSourceDir: File = File(context.applicationInfo.sourceDir)
        var useBridges: Boolean = false
    }
}
