package com.nighthawkapps.lib.android.ui.screen.scan.util

import android.graphics.ImageFormat
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer

/**
 * Decodes QR frames with ZXing inside the camera pipeline.
 * Overlay / frame UI lives in `ScanView` (see issue #437 on GitHub).
 */
class QrCodeAnalyzer(
    private val cropRatio: Float = 1.0f,
    private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {
    private val supportedImageFormats =
        listOf(
            ImageFormat.YUV_420_888,
            ImageFormat.YUV_422_888,
            ImageFormat.YUV_444_888
        )

    override fun analyze(image: ImageProxy) {
        image.use {
            if (image.format in supportedImageFormats) {
                val bytes =
                    image.planes
                        .first()
                        .buffer
                        .toByteArray()

                val minDim = minOf(image.width, image.height)
                val cropSize = (minDim * cropRatio).toInt()
                val left = (image.width - cropSize) / 2
                val top = (image.height - cropSize) / 2

                val source =
                    PlanarYUVLuminanceSource(
                        bytes,
                        image.width,
                        image.height,
                        left,
                        top,
                        cropSize,
                        cropSize,
                        false
                    )

                val binaryBmp = BinaryBitmap(HybridBinarizer(source))

                runCatching {
                    val result =
                        MultiFormatReader()
                            .apply {
                                setHints(
                                    mapOf(
                                        DecodeHintType.POSSIBLE_FORMATS to
                                            arrayListOf(
                                                BarcodeFormat.QR_CODE
                                            )
                                    )
                                )
                            }.decode(binaryBmp)

                    onQrCodeScanned(result.text)
                }.onFailure {
                    // failed to found QR code in current frame
                }
            }
        }
    }

    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()
        return ByteArray(remaining()).also {
            get(it)
        }
    }
}
