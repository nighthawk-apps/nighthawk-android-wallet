package com.nighthawkapps.lib.android.sdk.daemon

import android.content.Context
import android.content.res.AssetManager
import android.os.Build
import android.os.Process
import com.nighthawkapps.lib.android.spackle.Twig
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

/**
 * Resolves packaged native executables for Android 10+ (W^X / SELinux).
 *
 * Binaries in [Context.getFilesDir] cannot be `exec()`'d (`execute_no_trans` denied on
 * `app_data_file`). Package them as JNI libs (`lib*_embedded.so`) with
 * `android:extractNativeLibs="true"` so they land under [android.content.pm.ApplicationInfo.nativeLibraryDir].
 */
internal object EmbeddedPackagedExecutable {
    /**
     * Optional expected SHA-256 (hex) per ABI for release integrity.
     * Empty map / missing ABI = skip check (debug). Release builds should populate
     * via BuildConfig from Gradle.
     */
    @Volatile
    var expectedSha256ByAbi: Map<String, String> = emptyMap()

    fun resolve(
        context: Context,
        jniLibFileName: String,
        assetSubdir: String,
        assetBinaryName: String,
        legacyRelativePath: String,
    ): File? {
        val app = context.applicationContext
        val fromJni = File(app.applicationInfo.nativeLibraryDir, jniLibFileName)
        if (fromJni.exists() && fromJni.length() > 0L) {
            return verifyOrNull(fromJni)
        }

        val fromCodeCache = extractAssetOnce(app, assetSubdir, assetBinaryName, File(app.codeCacheDir, jniLibFileName))
        if (fromCodeCache != null) {
            return verifyOrNull(fromCodeCache)
        }

        return extractAssetOnce(
            app,
            assetSubdir,
            assetBinaryName,
            File(app.filesDir, legacyRelativePath),
        )?.let { verifyOrNull(it) }
    }

    private fun verifyOrNull(file: File): File? {
        val expected =
            expectedSha256ByAbi[Build.SUPPORTED_ABIS.firstOrNull().orEmpty()]
                ?: expectedSha256ByAbi[abiFolder()]
        if (expected.isNullOrBlank()) {
            return file
        }
        val actual = sha256Hex(file) ?: return null
        if (!actual.equals(expected, ignoreCase = true)) {
            Twig.error {
                "Embedded executable SHA-256 mismatch for ${file.name}: refusing exec"
            }
            return null
        }
        return file
    }

    private fun abiFolder(): String = mapAbiToAssetFolder(Build.SUPPORTED_ABIS.firstOrNull().orEmpty()).orEmpty()

    private fun sha256Hex(file: File): String? =
        try {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    md.update(buf, 0, n)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Twig.warn { "SHA-256 failed for ${file.absolutePath}: $e" }
            null
        }

    /**
     * argv for [ProcessBuilder] when running a PIE binary packaged as `lib*_embedded.so`.
     * Uses the system dynamic linker so Android loads the ELF like a normal executable.
     */
    fun commandLine(
        executable: File,
        vararg args: String,
    ): List<String> {
        val path = executable.absolutePath
        val needsLinker = path.endsWith(".so")
        return if (needsLinker) {
            val linker =
                if (Build.SUPPORTED_64_BIT_ABIS.contains(Build.SUPPORTED_ABIS.firstOrNull()) ||
                    Process.is64Bit()
                ) {
                    "/system/bin/linker64"
                } else {
                    "/system/bin/linker"
                }
            listOf(linker, path) + args
        } else {
            listOf(path) + args
        }
    }

    fun hasBundled(
        context: Context,
        jniLibFileName: String,
        assetSubdir: String,
        assetBinaryName: String,
    ): Boolean {
        val app = context.applicationContext
        if (File(app.applicationInfo.nativeLibraryDir, jniLibFileName).exists()) {
            return true
        }
        val assets = app.assets
        for (abi in Build.SUPPORTED_ABIS) {
            val folder = mapAbiToAssetFolder(abi) ?: continue
            if (assetExists(assets, "$assetSubdir/$folder/$assetBinaryName")) {
                return true
            }
        }
        return false
    }

    private fun extractAssetOnce(
        context: Context,
        assetSubdir: String,
        assetBinaryName: String,
        out: File,
    ): File? {
        val folder =
            resolveAssetFolderForDevice(context.assets, assetSubdir, assetBinaryName)
                ?: return null
        val assetPath = "$assetSubdir/$folder/$assetBinaryName"
        if (!assetExists(context.assets, assetPath)) {
            return null
        }
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
            Twig.warn { "Embedded executable: chmod failed for ${out.absolutePath}" }
        }
        return out
    }

    private fun resolveAssetFolderForDevice(
        assets: AssetManager,
        assetSubdir: String,
        assetBinaryName: String,
    ): String? {
        for (abi in Build.SUPPORTED_ABIS) {
            val folder = mapAbiToAssetFolder(abi) ?: continue
            if (assetExists(assets, "$assetSubdir/$folder/$assetBinaryName")) {
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
        path: String,
    ): Boolean =
        try {
            assets.open(path).close()
            true
        } catch (_: IOException) {
            false
        }
}
