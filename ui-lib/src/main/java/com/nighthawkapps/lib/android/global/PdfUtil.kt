package com.nighthawkapps.lib.android.global

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.nighthawkapps.lib.android.spackle.Twig
import com.nighthawkapps.lib.android.ui.R
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.File
import java.io.IOException
import java.security.Security
import java.text.SimpleDateFormat
import java.util.Date

object PdfUtil {
    private const val PDF_FILE_NAME = "NighthawkSeedWords.pdf"
    private const val PDF_CLEANUP_DELAY_MS = 120_000L

    private val coroutineExceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            Twig.error(throwable) { "PdfUtil coroutine failure" }
        }

    fun exportPasswordProtectedPdf(
        context: Context,
        password: String,
        seedWords: List<String>,
        birthDay: Long?
    ) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch(coroutineExceptionHandler) {
            PDFBoxResourceLoader.init(appContext)
            val stringBuilder = StringBuilder()
            val listOfSeeds = mutableListOf<String>()
            val noOfWordsPerLineToPrint = 6
            val pdfFile = File(appContext.cacheDir, PDF_FILE_NAME)
            try {
                seedWords.forEachIndexed { index, word ->
                    stringBuilder
                        .append(index + 1)
                        .append(". ")
                        .append(word)
                        .append("     ")
                    if ((index + 1) % noOfWordsPerLineToPrint == 0) {
                        listOfSeeds.add(stringBuilder.toString())
                        stringBuilder.clear()
                    }
                }
                if (stringBuilder.isNotEmpty()) {
                    listOfSeeds.add(stringBuilder.toString())
                    stringBuilder.clear()
                }
                if (pdfFile.exists()) {
                    pdfFile.delete()
                }

                val keyLength = 128 // 128 bit is the highest currently supported

                // Limit permissions of those without the password
                val accessPermission = AccessPermission()
                accessPermission.setCanPrint(false)

                // Sets the owner password and user password
                val standardProtectionPolicy = StandardProtectionPolicy(password, password, accessPermission)
                standardProtectionPolicy.encryptionKeyLength = keyLength
                standardProtectionPolicy.permissions = accessPermission
                standardProtectionPolicy.isPreferAES = true
                if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                    Security.addProvider(BouncyCastleProvider())
                }

                val font: PDFont = PDType1Font.HELVETICA
                val document = PDDocument()
                val page = PDPage()
                document.addPage(page)

                var created = false
                try {
                    val contentStream = PDPageContentStream(document, page)
                    val headingFontSize = 16f
                    val subHeadingFontSize = 14f
                    val bodyFontSize = 12f
                    val leading: Float = 1.5f * headingFontSize
                    val seedWordXOffset = 50f

                    contentStream.beginText()
                    contentStream.setNonStrokingColor(0f, 0f, 0f)
                    contentStream.setFont(font, headingFontSize)
                    contentStream.newLineAtOffset(50f, 700f)

                    // Title
                    contentStream.showText("These seed words restore your Nighthawk / DarkFi-oriented wallet: ")

                    // Seed Words
                    contentStream.setFont(font, bodyFontSize)
                    contentStream.newLineAtOffset(seedWordXOffset, -leading)
                    for (wordLine in listOfSeeds) {
                        contentStream.newLineAtOffset(0f, -leading)
                        contentStream.showText(wordLine)
                    }

                    // Wallet Birthday
                    contentStream.setFont(font, subHeadingFontSize)
                    contentStream.newLineAtOffset(-seedWordXOffset, -leading)
                    contentStream.showText("Wallet Birthday: $birthDay")

                    // PDF generated time
                    val dateFormatter =
                        SimpleDateFormat(
                            appContext.getString(R.string.transaction_history_format_date_time_brief),
                            appContext.resources.configuration.locales[0]
                        )
                    contentStream.newLineAtOffset(0f, -leading)
                    contentStream.showText("Backup PDF generated at: ${dateFormatter.format(Date())}")

                    contentStream.endText()
                    contentStream.close()

                    // Save the final pdf document to a file
                    document.protect(standardProtectionPolicy)
                    document.save(pdfFile)
                    created = true
                } catch (e: IOException) {
                    pdfFile.delete()
                    Twig.warn(e) { "PdfBox exception thrown while creating PDF" }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(appContext, "PDF creation failed", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    runCatching { document.close() }
                }

                // Share the file
                if (created && pdfFile.exists()) {
                    val fileURI =
                        FileProvider.getUriForFile(
                            appContext,
                            appContext.packageName + ".fileprovider",
                            pdfFile
                        )
                    val shared =
                        withContext(Dispatchers.Main) {
                            shareFile(context, fileURI)
                        }
                    if (!shared) {
                        pdfFile.delete()
                    } else {
                        scheduleCleanup(pdfFile)
                    }
                }
            } catch (e: Exception) {
                pdfFile.delete()
                Twig.warn(e) { "PDF creation failed" }
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "PDF creation failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun scheduleCleanup(pdfFile: File) {
        CoroutineScope(Dispatchers.IO).launch(coroutineExceptionHandler) {
            delay(PDF_CLEANUP_DELAY_MS)
            if (pdfFile.exists()) {
                pdfFile.delete()
            }
        }
    }

    private fun shareFile(
        context: Context,
        fileURI: Uri
    ): Boolean =
        try {
            val sendIntent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, fileURI)
                    clipData = ClipData.newRawUri(PDF_FILE_NAME, fileURI)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            val intentChooser =
                Intent.createChooser(sendIntent, "Share File").apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
            context.startActivity(intentChooser)
            true
        } catch (e: Exception) {
            Twig.warn(e) { "PDF sharing failed" }
            Toast.makeText(context.applicationContext, "PDF sharing failed", Toast.LENGTH_SHORT).show()
            false
        }
}
