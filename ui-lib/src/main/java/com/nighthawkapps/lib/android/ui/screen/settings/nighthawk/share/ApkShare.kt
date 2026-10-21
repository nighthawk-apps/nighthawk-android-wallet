package com.nighthawkapps.lib.android.ui.screen.settings.nighthawk.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Shares the installed Nighthawk APK with a nearby device, like bitchat's "share app" sheet.
 *
 * Stock Android's Bluetooth receiver only accepts a fixed list of MIME types, which includes
 * application/zip but not APKs, so the Bluetooth route sends the APK inside a zip. Other share
 * targets (Quick Share, Samsung Bluetooth, messengers) get the raw APK.
 */
object ApkShare {
    const val APK_MIME = "application/vnd.android.package-archive"
    const val ZIP_MIME = "application/zip"

    private const val SHARE_DIR = "apk_share"

    data class InstalledApk(
        val base: File,
        val splits: List<File>,
        val versionName: String,
    ) {
        val files: List<File> get() = listOf(base) + splits
        val isSplit: Boolean get() = splits.isNotEmpty()
        val totalBytes: Long get() = files.sumOf { it.length() }
    }

    fun installedApk(context: Context): InstalledApk {
        val info = context.applicationInfo
        @Suppress("DEPRECATION")
        val versionName =
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        return InstalledApk(
            base = File(info.sourceDir),
            splits = info.splitSourceDirs.orEmpty().map(::File),
            versionName = versionName,
        )
    }

    fun apkFileName(versionName: String): String = "Nighthawk-${sanitize(versionName)}.apk"

    fun zipFileName(versionName: String): String = "Nighthawk-${sanitize(versionName)}-apk.zip"

    /** ABIs with native libraries in [apk]; empty means the APK runs on any CPU. */
    fun nativeAbis(apk: File): Set<String> =
        ZipFile(apk).use { zip ->
            zip.entries().asSequence()
                .map { it.name }
                .filter { it.startsWith("lib/") && it.endsWith(".so") }
                .mapNotNull { it.split('/').getOrNull(1) }
                .toSortedSet()
        }

    /** Copies the APK(s) into the FileProvider-visible cache, replacing any earlier share. */
    fun copyForSharing(context: Context, apk: InstalledApk): List<File> {
        val dir = shareDir(context)
        if (!apk.isSplit) {
            val out = File(dir, apkFileName(apk.versionName))
            apk.base.copyTo(out, overwrite = true)
            return listOf(out)
        }
        return apk.files.map { src ->
            val name = if (src == apk.base) apkFileName(apk.versionName) else src.name
            src.copyTo(File(dir, name), overwrite = true)
        }
    }

    /** Wraps the APK(s) in a zip so stock Bluetooth receivers accept the transfer. */
    fun zipForBluetooth(context: Context, apk: InstalledApk): File {
        val out = File(shareDir(context), zipFileName(apk.versionName))
        writeZip(
            out,
            apk.files.map { src -> (if (src == apk.base) apkFileName(apk.versionName) else src.name) to src },
        )
        return out
    }

    internal fun writeZip(out: File, entries: List<Pair<String, File>>) {
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            // APKs are already compressed; deflating them again only costs time.
            zip.setLevel(Deflater.NO_COMPRESSION)
            entries.forEach { (name, src) ->
                zip.putNextEntry(ZipEntry(name))
                src.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Sends the zip at [zipUri] straight to the system Bluetooth app, or null if there is none. */
    fun bluetoothIntent(context: Context, zipUri: Uri): Intent? {
        val intent = sendIntent(ZIP_MIME, listOf(zipUri))
        val target =
            context.packageManager.queryIntentActivities(intent, 0)
                .firstOrNull { it.activityInfo.packageName.contains("bluetooth", ignoreCase = true) }
                ?: return null
        return intent.setClassName(target.activityInfo.packageName, target.activityInfo.name)
    }

    fun chooserIntent(context: Context, files: List<File>, title: String): Intent {
        val mime = if (files.all { it.extension == "apk" }) APK_MIME else ZIP_MIME
        val send = sendIntent(mime, files.map { uriFor(context, it) })
        return Intent.createChooser(send, title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    internal fun sendIntent(mime: String, uris: List<Uri>): Intent {
        require(uris.isNotEmpty())
        val intent =
            if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.single())
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        val clip = ClipData.newRawUri("", uris.first())
        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        return intent
            .setType(mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .also { it.clipData = clip }
    }

    private fun shareDir(context: Context): File =
        File(context.cacheDir, SHARE_DIR).apply {
            deleteRecursively()
            mkdirs()
        }

    private fun sanitize(versionName: String): String = versionName.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
