@file:Suppress("ReturnCount", "UseCheckOrError")

/*
 * Starts and monitors the tor-android process.
 */

package com.nighthawkapps.lib.android.sdk.tor.embedded

import com.jaredrummler.android.shell.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileNotFoundException
import java.util.logging.Level
import java.util.logging.Logger

internal class EmbeddedTorOperator(
    private val torSettings: EmbeddedTorDomain.Settings,
    private val onStatusUpdate: (EmbeddedTorDomain.Info) -> Unit,
) : EmbeddedTorControl.Listener {
    private val logger = Logger.getLogger("EmbeddedTorOperator")
    val torInfo = EmbeddedTorDomain.Info(EmbeddedTorDomain.Connection())

    private var torControl: EmbeddedTorControl? = null
    private lateinit var resManager: EmbeddedTorResourceManager
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun statusUpdate(torInfo: EmbeddedTorDomain.Info) {
        onStatusUpdate(torInfo)
    }

    fun start() {
        try {
            resManager = EmbeddedTorResourceManager(torSettings)
            val fileTorBin = resManager.installResources()
            val success = fileTorBin != null && fileTorBin.canExecute()

            if (!success) {
                throw FileNotFoundException("libtor not found or not executable")
            }

            torInfo.isInstalled = true
            eventMonitor(info = torInfo, msg = "Tor install success.")

            killTorProcess()

            if (!runTorShellCmd(resManager.fileTor, resManager.fileTorrcCustom)) {
                throw IllegalStateException("tor failed to start")
            }

            eventMonitor(msg = "Successfully verified config")

            Thread.sleep(100)

            torControl =
                EmbeddedTorControl(
                    resManager.fileTorControlPort,
                    torSettings.appDataDir,
                    this,
                    torInfo,
                )

            torInfo.status = EmbeddedEntityStatus.RUNNING
            eventMonitor(info = torInfo, msg = "Tor started")

            torControl?.let { tc ->
                ioScope.launch {
                    try {
                        tc.connectBlocking(40)
                    } catch (e: Throwable) {
                        torInfo.processId = -1
                        eventMonitor(
                            torInfo,
                            Level.SEVERE,
                            "control connect failed: ${e.message}",
                        )
                    }
                }
            }
        } catch (e: Exception) {
            torInfo.processId = -1
            torInfo.connection.status = EmbeddedConnectionStatus.FAILED
            onStatusUpdate(torInfo)
            eventMonitor(torInfo, Level.SEVERE, "Error starting Tor: ${e.message}")
        }
    }

    fun stop(): Boolean =
        try {
            var result = torControl?.shutdownTor() ?: false
            if (!result) {
                result = killTorProcess()
            }
            torInfo.status = EmbeddedEntityStatus.STOPPED
            eventMonitor(torInfo, Level.INFO, "Tor stopped")
            result
        } catch (e: Exception) {
            eventMonitor(torInfo, Level.SEVERE, "Tor stop error: ${e.message}")
            false
        }

    private fun eventMonitor(
        info: EmbeddedTorDomain.Info? = null,
        level: Level = Level.INFO,
        msg: String? = null,
    ) {
        msg?.let { logger.log(level, it) }
        info?.let {
            it.statusMessage = msg
            onStatusUpdate(it)
        }
    }

    private fun killTorProcess(): Boolean =
        try {
            if (::resManager.isInitialized) {
                EmbeddedTorProcessUtils.killProcess(resManager.fileTor)
            }
            true
        } catch (_: Exception) {
            false
        }

    @Throws(Exception::class)
    private fun runTorShellCmd(
        fileTor: File,
        fileTorrc: File,
    ): Boolean {
        val appCacheHome: File = torSettings.appDataDir

        if (!fileTorrc.exists()) {
            eventMonitor(msg = "torrc not installed: " + fileTorrc.canonicalPath)
            return false
        }
        val torCmdString =
            (
                fileTor.canonicalPath +
                    " DataDirectory " + appCacheHome.canonicalPath +
                    " --defaults-torrc " + fileTorrc
            )

        var exitCode: Int
        exitCode =
            try {
                exec("$torCmdString --verify-config")
            } catch (e: Exception) {
                eventMonitor(msg = "Tor verify-config failed: ${e.message}")
                return false
            }

        if (exitCode != 0) {
            eventMonitor(msg = "Tor configuration did not verify:$exitCode")
            return false
        }

        exitCode =
            try {
                exec(torCmdString)
            } catch (e: Exception) {
                eventMonitor(msg = "Tor unable to start: ${e.message}")
                return false
            }

        if (exitCode != 0) {
            eventMonitor(msg = "Tor did not start. Exit:$exitCode")
            return false
        }

        return true
    }

    @Throws(Exception::class)
    private fun exec(cmd: String): Int {
        val shellResult = Shell.run("sh", "-c", cmd)
        if (!shellResult.isSuccessful) {
            throw Exception(
                "Error: ${shellResult.exitCode} ERR=${shellResult.stderr} OUT=${shellResult.stdout}",
            )
        }
        eventMonitor(msg = "Result:$shellResult")
        return shellResult.exitCode
    }
}
