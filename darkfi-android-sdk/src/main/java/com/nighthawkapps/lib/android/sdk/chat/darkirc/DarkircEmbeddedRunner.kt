@file:Suppress("ReturnCount")

package com.nighthawkapps.lib.android.sdk.chat.darkirc

import android.content.Context
import android.content.res.AssetManager
import android.os.Build
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatPreferences
import com.nighthawkapps.lib.android.spackle.Twig
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs a **packaged** `darkirc` binary from assets (`darkirc/<abi>/darkirc_exec`) with a generated
 * config under [Context.getFilesDir].
 *
 * Binaries are **not** committed by default; see `scripts/build-darkirc-android.sh` and
 * `docs/darkirc-embedded-android.md`.
 */
@Suppress("TooManyFunctions")
object DarkircEmbeddedRunner {
    private const val ASSET_SUBDIR = "darkirc"
    private const val BINARY_NAME = "darkirc_exec"
    private const val WORKING_SUBDIR = "darkirc"
    private val processRef = AtomicReference<Process?>()

    @JvmStatic
    fun hasBundledBinary(context: Context): Boolean {
        val assets = context.assets
        for (abi in Build.SUPPORTED_ABIS) {
            val folder = mapAbiToAssetFolder(abi) ?: continue
            if (assetExists(assets, "$ASSET_SUBDIR/$folder/$BINARY_NAME")) {
                return true
            }
        }
        return false
    }

    /**
     * Returns the app-private executable if present, or null if the binary is not packaged.
     */
    @JvmStatic
    fun ensureExecutable(context: Context): File? {
        val folder =
            resolveAssetFolderForDevice(context.assets)
                ?: run {
                    Twig.warn { "Embedded darkirc: no binary for ABI ${Build.SUPPORTED_ABIS.joinToString()}" }
                    return null
                }
        val assetPath = "$ASSET_SUBDIR/$folder/$BINARY_NAME"
        if (!assetExists(context.assets, assetPath)) {
            Twig.warn { "Embedded darkirc: missing asset $assetPath" }
            return null
        }
        val out = File(context.filesDir, "$WORKING_SUBDIR/bin/darkirc")
        out.parentFile?.mkdirs()
        val lengthMatch: Boolean =
            try {
                context.assets.openFd(assetPath).use { fd ->
                    out.exists() && out.length() == fd.length
                }
            } catch (_: IOException) {
                false
            }
        if (!lengthMatch) {
            context.assets.open(assetPath).use { input ->
                FileOutputStream(out).use { output -> input.copyTo(output) }
            }
        }
        if (!out.setExecutable(true, false)) {
            Twig.error { "Embedded darkirc: chmod failed for ${out.absolutePath}" }
            return null
        }
        return out
    }

    /**
     * Writes [DarkircEmbeddedConfigGenerator] output to [Context.getFilesDir]/darkirc/darkirc_config.toml
     */
    @JvmStatic
    fun ensureConfigFile(context: Context): File {
        val app = context.applicationContext
        val root = File(app.filesDir, WORKING_SUBDIR)
        root.mkdirs()
        File(root, "p2p").mkdirs()
        File(root, "darkirc_db").mkdirs()
        val config = File(root, "darkirc_config.toml")
        val text =
            DarkircEmbeddedConfigGenerator.buildToml(
                root,
                tcpListenUriForEmbedded(app),
                DarkircCryptoStore.load(app),
            )
        if (!config.exists() || config.readText() != text) {
            config.writeText(text)
        }
        return config
    }

    /**
     * Writes `darkirc_config.toml` with `irc_listen` matching chat IRC host/port prefs, optionally
     * stops a running daemon if the on-disk listener config no longer matches (darkirc does not
     * hot-reload), then ensures a process is running.
     */
    @Synchronized
    @JvmStatic
    fun syncEmbeddedIrcListenConfigAndEnsureRunning(context: Context): Boolean {
        val app = context.applicationContext
        ensureExecutable(app) ?: return false
        val root = File(app.filesDir, WORKING_SUBDIR)
        val desiredToml =
            DarkircEmbeddedConfigGenerator.buildToml(
                root,
                tcpListenUriForEmbedded(app),
                DarkircCryptoStore.load(app),
            )
        val config = File(root, "darkirc_config.toml")
        val onDiskMatches =
            config.exists() && runCatching { config.readText() }.getOrNull() == desiredToml
        val daemonAlive = processRef.get()?.isAlive == true
        if (daemonAlive && !onDiskMatches) {
            stop()
        }
        ensureConfigFile(app)
        return startIfPossible(app)
    }

    private fun tcpListenUriForEmbedded(context: Context): String {
        val port =
            DarkfiChatPreferences(context.applicationContext).ircServerPort
        return "tcp://127.0.0.1:$port"
    }

    @Synchronized
    @JvmStatic
    fun isDaemonProcessAlive(): Boolean = processRef.get()?.isAlive == true

    @Synchronized
    @JvmStatic
    fun startIfPossible(context: Context): Boolean {
        if (processRef.get()?.isAlive == true) {
            return true
        }
        val app = context.applicationContext
        val exe = ensureExecutable(app) ?: return false
        val cfg = ensureConfigFile(app)
        val work = File(app.filesDir, WORKING_SUBDIR)
        return try {
            val pb =
                ProcessBuilder(
                    exe.absolutePath,
                    "--config",
                    cfg.absolutePath,
                )
            pb.directory(work)
            pb.environment()["HOME"] = work.absolutePath
            pb.environment()["TMPDIR"] = app.cacheDir.absolutePath
            pb.redirectErrorStream(true)
            val proc = pb.start()
            processRef.set(proc)
            Thread({ drainProcessStream(proc) }, "darkirc-stdout").start()
            Twig.info { "Embedded darkirc started (pid try — ${tryUnixPid(proc)})" }
            true
        } catch (e: Exception) {
            Twig.error(e) { "Embedded darkirc failed to start" }
            false
        }
    }

    @Synchronized
    @JvmStatic
    fun stop() {
        processRef.getAndSet(null)?.destroy()
    }

    private fun drainProcessStream(proc: Process) {
        try {
            proc.inputStream.bufferedReader().use { reader ->
                reader.forEachLine { line -> Twig.info { "darkirc: $line" } }
            }
        } catch (_: IOException) {
        }
        try {
            val code = proc.waitFor()
            Twig.warn { "Embedded darkirc exited with code $code" }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            processRef.compareAndSet(proc, null)
        }
    }

    private fun tryUnixPid(proc: Process): String =
        try {
            val m = proc.javaClass.getMethod("pid")
            val v = m.invoke(proc)
            when (v) {
                is Number -> v.toLong().toString()
                null -> "?"
                else -> v.toString()
            }
        } catch (_: ReflectiveOperationException) {
            "?"
        }

    private fun resolveAssetFolderForDevice(assets: AssetManager): String? {
        for (abi in Build.SUPPORTED_ABIS) {
            val folder = mapAbiToAssetFolder(abi) ?: continue
            if (assetExists(assets, "$ASSET_SUBDIR/$folder/$BINARY_NAME")) {
                return folder
            }
        }
        return null
    }

    private fun mapAbiToAssetFolder(abi: String): String? =
        when (abi) {
            "arm64-v8a" -> "arm64-v8a"
            "armeabi-v7a" -> "armeabi-v7a"
            "x86_64" -> "x86_64"
            "x86" -> "x86"
            else -> null
        }

    private fun assetExists(
        assets: AssetManager,
        path: String
    ): Boolean =
        try {
            assets.open(path).close()
            true
        } catch (_: IOException) {
            false
        }
}
