@file:Suppress("ReturnCount", "MaxLineLength")

/*
 * Copies bundled Tor assets (torrc, geoip, native binary) into app storage.
 */

package com.nighthawkapps.lib.android.sdk.tor.embedded

import com.nighthawkapps.lib.android.spackle.Twig
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintStream
import java.util.concurrent.TimeoutException
import java.util.zip.ZipInputStream

internal class EmbeddedTorResourceManager(
    private val torSettings: EmbeddedTorDomain.Settings,
) {
    private val loggerTag = "EmbeddedTorResources"

    lateinit var fileTor: File
    lateinit var fileTorrcCustom: File
    lateinit var fileTorControlPort: File
    private lateinit var fileTorrc: File

    @Throws(IOException::class, TimeoutException::class)
    fun installResources(): File? {
        if (!torSettings.appFilesDir.exists()) {
            torSettings.appFilesDir.mkdirs()
        }
        if (!torSettings.appDataDir.exists()) {
            torSettings.appDataDir.mkdirs()
        }

        fileTorControlPort = File(torSettings.appFilesDir, EmbeddedTorConstants.TOR_CONTROL_PORT_FILE)

        installGeoIP()

        fileTorrc =
            assetToFile(
                EmbeddedTorConstants.COMMON_ASSET_KEY + EmbeddedTorConstants.TORRC_ASSET_KEY,
                EmbeddedTorConstants.TORRC_ASSET_KEY,
                false,
                false,
            )

        updateTorrcCustomFile()?.let {
            fileTorrcCustom = it
        }

        fileTor = File(torSettings.appNativeDir, EmbeddedTorConstants.TOR_ASSET_KEY + ".so")

        if (fileTor.exists()) {
            if (fileTor.canExecute()) {
                return fileTor
            } else {
                EmbeddedTorFileUtils.setExecutable(fileTor)
                if (fileTor.canExecute()) {
                    return fileTor
                }
            }

            val insStream: InputStream = FileInputStream(fileTor)
            streamToFile(insStream, fileTor, false, true)
            EmbeddedTorFileUtils.setExecutable(fileTor)

            if (fileTor.exists() && fileTor.canExecute()) {
                return fileTor
            }

            return EmbeddedTorNativeLoader
                .loadNativeBinary(
                    torSettings.appNativeDir,
                    torSettings.appSourceDir,
                    EmbeddedTorConstants.TOR_ASSET_KEY,
                    File(torSettings.appFilesDir, EmbeddedTorConstants.TOR_ASSET_KEY),
                )?.let { alt ->
                    EmbeddedTorFileUtils.setExecutable(alt)
                    if (alt.canExecute()) {
                        fileTor = alt
                        alt
                    } else {
                        null
                    }
                }
        } else {
            Twig.warn { "$loggerTag: bundled tor binary missing at ${fileTor.path}" }
        }
        return null
    }

    @Throws(IOException::class, TimeoutException::class)
    private fun updateTorrcCustomFile(): File? {
        val extraLines = StringBuffer()
        extraLines.append("\n")
        extraLines.append("RunAsDaemon 1").append('\n')
        extraLines.append("AvoidDiskWrites 1").append('\n')
        extraLines.append("ControlPortWriteToFile ").append(fileTorControlPort.absolutePath).append('\n')
        extraLines.append("ControlPort Auto").append('\n')
        extraLines.append("SOCKSPort ").append(checkPortOrAuto(EmbeddedTorConstants.SOCKS_PROXY_PORT_DEFAULT)).append('\n')
        extraLines.append("ReducedConnectionPadding 1").append('\n')
        extraLines.append("ReducedCircuitPadding 1").append('\n')
        extraLines.append("SafeSocks 0").append('\n')
        extraLines.append("TestSocks 0").append('\n')
        extraLines.append("TransPort 0").append('\n')
        extraLines.append("HTTPTunnelPort ").append(checkPortOrAuto(EmbeddedTorConstants.HTTP_PROXY_PORT_DEFAULT)).append('\n')
        extraLines.append("DNSPort ").append(checkPortOrAuto(EmbeddedTorConstants.TOR_DNS_PORT_DEFAULT)).append('\n')
        extraLines.append("CookieAuthentication 1").append('\n')
        extraLines.append("DisableNetwork 0").append('\n')

        val fileTorRcCustom = File(fileTorrc.absolutePath + ".custom")
        val success = updateTorConfigCustom(fileTorRcCustom, extraLines.toString())

        return if (success && fileTorRcCustom.exists()) {
            fileTorRcCustom
        } else {
            null
        }
    }

    private fun checkPortOrAuto(portString: String): String {
        if (!portString.lowercase().contentEquals("auto")) {
            var isPortUsed = true
            var port = portString.toInt()
            while (isPortUsed) {
                isPortUsed =
                    EmbeddedTorNetworkUtils.isPortOpen(EmbeddedTorConstants.IP_LOCALHOST, port, 500)
                if (isPortUsed) {
                    port++
                }
            }
            return port.toString() + ""
        }
        return portString
    }

    @Throws(IOException::class, FileNotFoundException::class, TimeoutException::class)
    fun updateTorConfigCustom(
        fileTorRcCustom: File,
        extraLines: String?,
    ): Boolean {
        if (fileTorRcCustom.exists()) {
            fileTorRcCustom.delete()
            Twig.debug { "$loggerTag: deleting existing torrc.custom" }
        } else {
            fileTorRcCustom.createNewFile()
        }
        val fos = FileOutputStream(fileTorRcCustom, false)
        val ps = PrintStream(fos)
        ps.print(extraLines)
        ps.close()
        return true
    }

    @Throws(IOException::class)
    private fun installGeoIP(): Boolean {
        assetToFile(
            EmbeddedTorConstants.COMMON_ASSET_KEY + EmbeddedTorConstants.GEOIP_ASSET_KEY,
            EmbeddedTorConstants.GEOIP_ASSET_KEY,
        )
        assetToFile(
            EmbeddedTorConstants.COMMON_ASSET_KEY + EmbeddedTorConstants.GEOIP6_ASSET_KEY,
            EmbeddedTorConstants.GEOIP6_ASSET_KEY,
        )
        return true
    }

    @Throws(IOException::class)
    private fun assetToFile(
        assetPath: String,
        assetKey: String,
        isZipped: Boolean = false,
        isExecutable: Boolean = false,
    ): File {
        val inpStream = torSettings.context.assets.open(assetPath)
        val outFile = File(torSettings.appFilesDir, assetKey)
        streamToFile(inpStream, outFile, false, isZipped)
        if (isExecutable) {
            EmbeddedTorFileUtils.setExecutable(outFile)
        }
        return outFile
    }

    @Throws(IOException::class)
    private fun streamToFile(
        stm: InputStream,
        outFile: File,
        append: Boolean,
        zip: Boolean,
    ): Boolean {
        var inpStream: InputStream = stm
        val buffer = ByteArray(EmbeddedTorConstants.FILE_WRITE_BUFFER_SIZE)
        var bytecount: Int
        val stmOut: OutputStream = FileOutputStream(outFile.absolutePath, append)
        var zis: ZipInputStream? = null
        if (zip) {
            zis = ZipInputStream(stm)
            zis.nextEntry
            inpStream = zis
        }
        while (inpStream.read(buffer).also { bytecount = it } > 0) {
            stmOut.write(buffer, 0, bytecount)
        }
        stmOut.close()
        inpStream.close()
        zis?.close()
        return true
    }
}
