package com.nighthawkapps.lib.android.ui.screen.receive.util

import androidx.compose.ui.graphics.ImageBitmap

interface QrCodeImageGenerator {
    fun generate(
        bitArray: BooleanArray,
        sizePixels: Int
    ): ImageBitmap
}
