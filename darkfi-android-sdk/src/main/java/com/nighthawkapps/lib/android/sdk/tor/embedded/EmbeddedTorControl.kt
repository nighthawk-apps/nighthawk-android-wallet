@file:Suppress("EmptyFunctionBlock", "ReturnCount", "DEPRECATION")

/*
 * jtorctl control session (ReactiveX removed in favor of [connectBlocking]).
 */

package com.nighthawkapps.lib.android.sdk.tor.embedded

import android.text.TextUtils
import net.freehaven.tor.control.EventHandler
import net.freehaven.tor.control.TorControlConnection
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileReader
import java.io.IOException
import java.net.Socket
import java.util.logging.Level
import java.util.logging.Logger

internal class EmbeddedTorControl(
    private val fileControlPort: File,
    private val appCacheHome: File,
    private val listener: Listener,
    val torInfo: EmbeddedTorDomain.Info,
) {
    interface Listener {
        fun statusUpdate(torInfo: EmbeddedTorDomain.Info)
    }

    private val logger = Logger.getLogger("EmbeddedTorControl")

    private val controlSocketTimeout = 60000
    private var controlConn: TorControlConnection? = null
    private var torEventHandler: TorEventHandler? = null
    private var torProcessId: Int = -1
    private val maxBootstrapCheckTries = 60

    fun eventMonitor(
        torInfoArg: EmbeddedTorDomain.Info? = null,
        msg: String? = null,
    ) {
        msg?.let { logger.info(it) }
        torInfoArg?.let {
            it.statusMessage = msg
            listener.statusUpdate(it)
        }
    }

    fun eventMonitor(
        torInfoArg: EmbeddedTorDomain.Info?,
        level: Level,
        msg: String?,
    ) {
        msg?.let { logger.log(level, it) }
        torInfoArg?.let {
            it.statusMessage = msg
            listener.statusUpdate(it)
        }
    }

    fun shutdownTor(): Boolean {
        if (!isConnectedToControl()) {
            return false
        }
        return try {
            controlConn?.shutdownTor("HALT")
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun isConnectedToControl(): Boolean = controlConn != null

    /**
     * Blocks until control auth succeeds or max tries exhausted (call from a worker thread).
     */
    fun connectBlocking(maxTries: Int): EmbeddedTorDomain.Connection {
        torInfo.connection.status = EmbeddedConnectionStatus.CONNECTING
        eventMonitor(torInfo)

        var attempt = 0
        while (controlConn == null && attempt++ < maxTries) {
            try {
                val controlPort = getControlPort()
                if (controlPort != -1) {
                    eventMonitor(msg = "Connecting to control port: $controlPort")
                    val torConnSocket = Socket(EmbeddedTorConstants.IP_LOCALHOST, controlPort)
                    torConnSocket.soTimeout = controlSocketTimeout
                    val conn = TorControlConnection(torConnSocket)
                    controlConn = conn
                    eventMonitor(msg = "SUCCESS connected to Tor control port.")
                    return configConnection(conn, torInfo)
                }
            } catch (e: Exception) {
                controlConn = null
                torInfo.connection.processId = -1
                torInfo.connection.status = EmbeddedConnectionStatus.FAILED
                eventMonitor(torInfo, Level.WARNING, "Error connecting to Tor control: ${e.localizedMessage}")
            }
            Thread.sleep(300)
        }
        return EmbeddedTorDomain.Connection(-1)
    }

    private fun configConnection(
        conn: TorControlConnection,
        torInfoArg: EmbeddedTorDomain.Info,
    ): EmbeddedTorDomain.Connection {
        try {
            val fileCookie = File(appCacheHome, EmbeddedTorConstants.TOR_CONTROL_COOKIE)
            if (fileCookie.exists()) {
                val cookie = ByteArray(fileCookie.length().toInt())
                DataInputStream(FileInputStream(fileCookie)).use { fis ->
                    fis.read(cookie)
                }
                conn.authenticate(cookie)
                val torProcId = conn.getInfo("process/pid")
                torProcessId = torProcId.toInt()
                torInfoArg.connection.processId = torProcessId
                eventMonitor(torInfoArg, Level.INFO, "SUCCESS - tor control processId:$torProcId")

                torEventHandler = TorEventHandler(this)
                torEventHandler?.let {
                    addEventHandler(conn, it)
                }
                return torInfoArg.connection
            } else {
                eventMonitor(msg = "Tor authentication cookie does not exist yet")
            }
        } catch (e: Exception) {
            controlConn = null
            torInfoArg.connection.processId = -1
            torInfoArg.connection.status = EmbeddedConnectionStatus.FAILED
            eventMonitor(torInfoArg, Level.SEVERE, "Error configuring Tor connection: ${e.localizedMessage}")
        }
        return EmbeddedTorDomain.Connection(-1)
    }

    @Synchronized
    fun onBootstrapped(torInfoArg: EmbeddedTorDomain.Info) {
        if (torInfoArg.connection.status != EmbeddedConnectionStatus.CONNECTED) {
            eventMonitor(msg = "Starting bootstrap status checks ...")
            var isSuccess: Int
            var tries = 1
            do {
                isSuccess = getBootStatus()
                Thread.sleep(900)
                tries++
            } while (isSuccess == 0 && tries <= maxBootstrapCheckTries)

            if (isSuccess == 1) {
                torInfoArg.connection.status = EmbeddedConnectionStatus.CONNECTED
                eventMonitor(torInfoArg, Level.INFO, "Tor bootstrapped 100%")
            } else if (isSuccess == -1 || tries >= maxBootstrapCheckTries) {
                torInfoArg.connection.status = EmbeddedConnectionStatus.FAILED
                shutdownTor()
                eventMonitor(torInfoArg)
            }
        }
    }

    @Synchronized
    fun getBootStatus(): Int {
        controlConn?.let {
            try {
                val phase: String? = it.getInfo("status/bootstrap-phase")
                eventMonitor(msg = "Boot status:$phase")
                if (phase != null && phase.contains("PROGRESS=100")) {
                    return 1
                } else {
                    return 0
                }
            } catch (e: IOException) {
                eventMonitor(msg = "getInfo failed: $e")
            }
        }
        return -1
    }

    private fun getControlPort(): Int {
        var result = -1
        try {
            if (fileControlPort.exists()) {
                eventMonitor(msg = "Reading control port file: " + fileControlPort.canonicalPath)
                BufferedReader(FileReader(fileControlPort)).use { bufferedReader ->
                    val line = bufferedReader.readLine()
                    if (line != null) {
                        val lineParts = line.split(":").toTypedArray()
                        if (lineParts.size > 1) {
                            result = lineParts[1].toInt()
                        }
                    }
                }
            } else {
                eventMonitor(
                    msg = "Control port file not ready yet: ${fileControlPort.canonicalPath}",
                )
            }
        } catch (_: FileNotFoundException) {
            eventMonitor(msg = "unable to get control port; file not found")
        } catch (_: Exception) {
            eventMonitor(msg = "unable to read control port config file")
        }
        return result
    }

    @Throws(Exception::class)
    private fun addEventHandler(
        conn: TorControlConnection,
        torEventHandler: TorEventHandler,
    ) {
        eventMonitor(msg = "adding control port event handler")
        conn.setEventHandler(torEventHandler)
        conn.setEvents(listOf("ORCONN", "CIRC", "NOTICE", "WARN", "ERR", "BW"))
        eventMonitor(msg = "SUCCESS added control port event handler")
    }

    private inner class TorEventHandler(
        private var torControl: EmbeddedTorControl,
    ) : EventHandler {
        override fun streamStatus(
            status: String?,
            streamID: String?,
            target: String?,
        ) {
        }

        override fun bandwidthUsed(
            read: Long,
            written: Long,
        ) {
        }

        override fun orConnStatus(
            status: String?,
            orName: String?,
        ) {
            status?.let {
                if (TextUtils.equals(it, "CONNECTED")) {
                    Thread {
                        torControl.onBootstrapped(torControl.torInfo)
                    }.start()
                } else if (TextUtils.equals(it, "FAILED")) {
                    torControl.torInfo.connection.status = EmbeddedConnectionStatus.FAILED
                    torControl.eventMonitor(torControl.torInfo)
                }
            }
        }

        override fun newDescriptors(orList: MutableList<String>?) {
        }

        override fun unrecognized(
            type: String?,
            msg: String?,
        ) {
        }

        override fun circuitStatus(
            status: String?,
            circID: String?,
            path: String?,
        ) {
        }

        override fun message(
            severity: String?,
            msg: String?,
        ) {
        }
    }
}
