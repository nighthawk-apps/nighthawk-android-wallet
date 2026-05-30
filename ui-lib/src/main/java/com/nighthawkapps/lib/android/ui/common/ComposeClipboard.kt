package com.nighthawkapps.lib.android.ui.common

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard

suspend fun Clipboard.setPlainText(text: String) {
    setClipEntry(ClipEntry(ClipData.newPlainText("", text)))
}

suspend fun Clipboard.pastePlainText(): String? {
    val clipData = getClipEntry()?.clipData
    return clipData
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.text
        ?.toString()
}
