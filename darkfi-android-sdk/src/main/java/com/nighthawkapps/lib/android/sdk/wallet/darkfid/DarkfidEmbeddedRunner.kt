@file:Suppress("ReturnCount")

package com.nighthawkapps.lib.android.sdk.wallet.darkfid

import android.content.Context
import com.nighthawkapps.lib.android.sdk.daemon.AppPrivateFilePermissions
import com.nighthawkapps.lib.android.sdk.daemon.EmbeddedPackagedExecutable
import com.nighthawkapps.lib.android.sdk.tor.AppTorCoordinator
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiNetwork
import com.nighthawkapps.lib.android.sdk.wallet.darkfiNetworkFromPackage
import com.nighthawkapps.lib.android.spackle.Twig
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs packaged **darkfid** from assets (`darkfid/<abi>/darkfid_exec`) with generated TOML under
 * [Context.getFilesDir]/darkfid — upstream `bin/app` expects an external daemon on loopback RPC.
 */
@Suppress("TooManyFunctions")
object DarkfidEmbeddedRunner {
    private const val ASSET_SUBDIR = "darkfid"
    private const val BINARY_NAME = "darkfid_exec"
    private const val JNI_LIB_NAME = "libdarkfid_embedded.so"
    private const val LEGACY_BIN_PATH = "darkfid/bin/darkfid"
    private const val WORKING_SUBDIR = "darkfid"
    private val processRef = AtomicReference<Process?>()

    @JvmStatic
    fun hasBundledBinary(context: Context): Boolean =
        EmbeddedPackagedExecutable.hasBundled(
            context,
            JNI_LIB_NAME,
            ASSET_SUBDIR,
            BINARY_NAME,
        )

    @JvmStatic
    fun ensureExecutable(context: Context): File? =
        EmbeddedPackagedExecutable.resolve(
            context,
            JNI_LIB_NAME,
            ASSET_SUBDIR,
            BINARY_NAME,
            LEGACY_BIN_PATH,
        )

    @JvmStatic
    fun ensureConfigFile(
        context: Context,
        network: DarkfiNetwork = darkfiNetworkFromPackage(context),
    ): File {
        val app = context.applicationContext
        val root = File(app.filesDir, WORKING_SUBDIR)
        root.mkdirs()
        File(root, "p2p").mkdirs()
        File(root, "chain_db").mkdirs()
        val config = File(root, "darkfid_config.toml")
        val text =
            DarkfidEmbeddedConfigGenerator.buildToml(
                root,
                network,
                AppTorCoordinator.resolveDarkfidP2pTransport(app),
            )
        if (!config.exists() || config.readText() != text) {
            config.writeText(text)
            AppPrivateFilePermissions.restrictToAppUid(config)
        }
        AppPrivateFilePermissions.restrictDirectoryToAppUid(root)
        return config
    }

    @Synchronized
    @JvmStatic
    fun syncConfigAndEnsureRunning(context: Context): Boolean {
        val app = context.applicationContext
        ensureExecutable(app) ?: return false
        val network = darkfiNetworkFromPackage(app)
        val root = File(app.filesDir, WORKING_SUBDIR)
        val desiredToml =
            DarkfidEmbeddedConfigGenerator.buildToml(
                root,
                network,
                AppTorCoordinator.resolveDarkfidP2pTransport(app),
            )
        val config = File(root, "darkfid_config.toml")
        val onDiskMatches =
            config.exists() && runCatching { config.readText() }.getOrNull() == desiredToml
        val daemonAlive = processRef.get()?.isAlive == true
        if (daemonAlive && !onDiskMatches) {
            stop()
        }
        ensureConfigFile(app, network)
        return startIfPossible(app)
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
                    EmbeddedPackagedExecutable.commandLine(
                        exe,
                        "--config",
                        cfg.absolutePath,
                    ),
                )
            pb.directory(work)
            pb.environment()["HOME"] = work.absolutePath
            pb.environment()["TMPDIR"] = app.cacheDir.absolutePath
            pb.redirectErrorStream(true)
            val proc = pb.start()
            processRef.set(proc)
            Thread({ drainProcessStream(proc, isDebuggable(app)) }, "darkfid-stdout").start()
            Twig.info { "Embedded darkfid started" }
            true
        } catch (e: IOException) {
            Twig.error(e) { "Embedded darkfid failed to start" }
            false
        }
    }

    @Synchronized
    @JvmStatic
    fun stop() {
        processRef.getAndSet(null)?.destroy()
    }

    private fun drainProcessStream(
        proc: Process,
        logLines: Boolean,
    ) {
        try {
            proc.inputStream.bufferedReader().use { reader ->
                reader.forEachLine { line ->
                    if (logLines) {
                        Twig.info { "darkfid: $line" }
                    }
                }
            }
        } catch (_: IOException) {
        }
        try {
            val code = proc.waitFor()
            Twig.warn { "Embedded darkfid exited with code $code" }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            processRef.compareAndSet(proc, null)
        }
    }

    private fun isDebuggable(context: Context): Boolean =
        (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
