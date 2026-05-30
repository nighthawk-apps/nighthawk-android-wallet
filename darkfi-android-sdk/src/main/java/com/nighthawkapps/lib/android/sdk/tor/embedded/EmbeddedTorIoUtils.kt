@file:Suppress("ReturnCount")

/*
 * File/process helpers for embedded Tor.
 */

package com.nighthawkapps.lib.android.sdk.tor.embedded

import android.os.Build
import com.nighthawkapps.lib.android.spackle.Twig
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.zip.ZipFile

internal object EmbeddedTorFileUtils {
    fun setExecutable(fileBin: File) {
        fileBin.setReadable(true)
        fileBin.setExecutable(true)
        fileBin.setWritable(false)
        fileBin.setWritable(true, true)
    }
}

internal object EmbeddedTorProcessUtils {
    @Throws(IOException::class)
    fun findProcessId(processName: String): Int {
        val procPs: Process = Runtime.getRuntime().exec(EmbeddedTorConstants.SHELL_CMD_PS)
        val reader = BufferedReader(InputStreamReader(procPs.inputStream))
        var line: String? = reader.readLine()
        while (line != null) {
            if (line.contains(processName)) {
                val lineParts = line.split("\\s+".toRegex()).toTypedArray()
                return try {
                    lineParts[1].toInt()
                } catch (_: NumberFormatException) {
                    lineParts[0].toInt()
                } finally {
                    try {
                        procPs.destroy()
                    } catch (_: Exception) {
                    }
                }
            }
            line = reader.readLine()
        }
        return -1
    }

    @Throws(Exception::class)
    fun killProcess(fileProcBin: File) {
        killProcess(fileProcBin, "-9")
    }

    @Throws(Exception::class)
    fun killProcess(
        fileProcBin: File,
        signal: String,
    ) {
        var procId: Int
        var killAttempts = 0
        while (findProcessId(fileProcBin.name).also { procId = it } != -1) {
            killAttempts++
            val pidString = procId.toString()
            try {
                Runtime.getRuntime().exec("busybox killall $signal ${fileProcBin.name}")
            } catch (_: IOException) {
            }
            killProcess(pidString, signal)
            try {
                Thread.sleep(1000)
            } catch (_: InterruptedException) {
            }
            if (killAttempts > 4) {
                throw Exception("Cannot kill: ${fileProcBin.absolutePath}")
            }
        }
    }

    @Throws(Exception::class)
    fun killProcess(
        pidString: String,
        signal: String,
    ) {
        try {
            Runtime.getRuntime().exec("kill $signal $pidString")
        } catch (_: IOException) {
        }
        try {
            Runtime.getRuntime().exec("toolbox kill $signal $pidString")
        } catch (_: IOException) {
        }
        try {
            Runtime.getRuntime().exec("busybox kill $signal $pidString")
        } catch (_: IOException) {
        }
    }
}

internal object EmbeddedTorNativeLoader {
    private const val TAG = "EmbeddedTorNativeLoader"

    private fun loadFromZip(
        appSourceDir: File,
        libName: String,
        destLocalFile: File,
        arch: String,
    ): Boolean {
        var zipFile: ZipFile? = null
        var stream: InputStream? = null
        try {
            zipFile = ZipFile(appSourceDir)
            var entry = zipFile.getEntry("lib/$arch/$libName.so")
            if (entry == null) {
                entry = zipFile.getEntry("jni/$arch/$libName.so")
                if (entry == null) {
                    throw Exception("Unable to find file in apk:lib/$arch/$libName")
                }
            }
            stream = zipFile.getInputStream(entry)
            val out: OutputStream = FileOutputStream(destLocalFile)
            val buf = ByteArray(4096)
            var len: Int
            while (stream.read(buf).also { len = it } > 0) {
                Thread.yield()
                out.write(buf, 0, len)
            }
            out.close()
            destLocalFile.setReadable(true, false)
            destLocalFile.setExecutable(true, false)
            destLocalFile.setWritable(true)
            return true
        } catch (e: Exception) {
            Twig.error(e) { "$TAG: loadFromZip failed" }
        } finally {
            try {
                stream?.close()
            } catch (_: Exception) {
            }
            try {
                zipFile?.close()
            } catch (_: Exception) {
            }
        }
        return false
    }

    fun loadNativeBinary(
        appNativeDir: File,
        appSourceDir: File,
        libName: String,
        destLocalFile: File?,
    ): File? {
        try {
            val fileNativeBin = File(appNativeDir.path, "$libName.so")
            if (fileNativeBin.exists()) {
                if (fileNativeBin.canExecute()) {
                    return fileNativeBin
                } else {
                    EmbeddedTorFileUtils.setExecutable(fileNativeBin)
                    if (fileNativeBin.canExecute()) {
                        return fileNativeBin
                    }
                }
            }
            var folder = Build.SUPPORTED_ABIS[0]
            val javaArch = System.getProperty("os.arch")
            if (javaArch != null && javaArch.contains("686")) {
                folder = "x86"
            }
            destLocalFile?.let {
                if (loadFromZip(appSourceDir, libName, it, folder)) {
                    return it
                }
            }
        } catch (e: Throwable) {
            Twig.error(e) { "$TAG: loadNativeBinary failed" }
        }
        return null
    }
}

internal object EmbeddedTorNetworkUtils {
    fun isPortOpen(
        ip: String?,
        port: Int,
        timeout: Int,
    ): Boolean =
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeout)
            }
            true
        } catch (_: ConnectException) {
            false
        } catch (_: Exception) {
            false
        }
}
