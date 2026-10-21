package com.nighthawkapps.lib.android.ui.screen.settings.nighthawk.share

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ApkShareTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val uri: Uri = Uri.parse("content://test.fileprovider/files/apk_share/Nighthawk-1.0-apk.zip")

    @Test
    fun fileNames_areVersionedAndSafe() {
        assertEquals("Nighthawk-1.2.3.apk", ApkShare.apkFileName("1.2.3"))
        assertEquals("Nighthawk-1.2.3_beta_1_-apk.zip", ApkShare.zipFileName("1.2.3 beta(1)"))
    }

    @Test
    fun nativeAbis_listsEveryBundledAbi() {
        val apk = fakeApk("lib/arm64-v8a/libdarkfi_mobile_ffi.so", "lib/x86_64/libdarkfi_mobile_ffi.so", "classes.dex")
        assertEquals(setOf("arm64-v8a", "x86_64"), ApkShare.nativeAbis(apk))
    }

    @Test
    fun nativeAbis_emptyWithoutNativeCode() {
        assertTrue(ApkShare.nativeAbis(fakeApk("classes.dex")).isEmpty())
    }

    @Test
    fun writeZip_wrapsApkByteForByte() {
        val apk = fakeApk("classes.dex")
        val out = temp.newFile("bundle.zip")
        ApkShare.writeZip(out, listOf("Nighthawk-1.0.apk" to apk))

        ZipFile(out).use { zip ->
            val entry = zip.getEntry("Nighthawk-1.0.apk")
            assertNotNull(entry)
            assertTrue(zip.getInputStream(entry).readBytes().contentEquals(apk.readBytes()))
        }
    }

    @Test
    fun sendIntent_singleFile_grantsReadAndCarriesApkMime() {
        val intent = ApkShare.sendIntent(ApkShare.APK_MIME, listOf(uri))
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals(ApkShare.APK_MIME, intent.type)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(uri, intent.clipData?.getItemAt(0)?.uri)
    }

    @Test
    fun sendIntent_splitApks_usesSendMultiple() {
        val second = Uri.parse("content://test.fileprovider/files/apk_share/split_config.arm64_v8a.apk")
        val intent = ApkShare.sendIntent(ApkShare.APK_MIME, listOf(uri, second))
        assertEquals(Intent.ACTION_SEND_MULTIPLE, intent.action)
        assertEquals(2, intent.clipData?.itemCount)
    }

    @Test
    fun bluetoothIntent_nullWhenNoBluetoothApp() {
        assertNull(ApkShare.bluetoothIntent(app, uri))
    }

    @Test
    fun bluetoothIntent_targetsSystemBluetoothWithZip() {
        val component = ComponentName("com.android.bluetooth", "com.android.bluetooth.opp.BluetoothOppLauncherActivity")
        shadowOf(app.packageManager).apply {
            addActivityIfNotPresent(component)
            addIntentFilterForActivity(
                component,
                IntentFilter(Intent.ACTION_SEND).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addDataType("*/*")
                },
            )
        }

        val intent = ApkShare.bluetoothIntent(app, uri)

        assertNotNull(intent)
        assertEquals(component, intent!!.component)
        assertEquals(ApkShare.ZIP_MIME, intent.type)
    }

    private fun fakeApk(vararg entries: String): File {
        val apk = temp.newFile("test-${System.nanoTime()}.apk")
        ZipOutputStream(apk.outputStream()).use { zip ->
            entries.forEach {
                zip.putNextEntry(ZipEntry(it))
                zip.write(byteArrayOf(1, 2, 3))
                zip.closeEntry()
            }
        }
        return apk
    }
}
