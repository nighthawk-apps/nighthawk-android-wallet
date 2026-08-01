package com.nighthawkapps.lib.android.spackle

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import android.widget.Toast
import kotlinx.coroutines.runBlocking

object ClipboardManagerUtil {
    fun copyToClipboard(
        context: Context,
        label: String,
        value: String
    ) {
        Twig.debug { "Copied to clipboard: label: $label" }
        val clipboardManager = context.getSystemService(ClipboardManager::class.java)
        val data =
            ClipData.newPlainText(
                label,
                value
            )

        if (AndroidApiVersion.isAtLeastT) {
            // Android 13+ (API 33+) supports a sensitive flag to suppress the visual preview
            data.description.extras =
                PersistableBundle().apply {
                    putBoolean("android.content.extra.IS_SENSITIVE", true)
                }
            clipboardManager.setPrimaryClip(data)
        } else {
            // Blocking call is fine here, as we just moved to the IO thread to satisfy theStrictMode on an older API
            runBlocking { clipboardManager.setPrimaryClipSuspend(data) }
            Toast
                .makeText(
                    context,
                    value,
                    Toast.LENGTH_SHORT
                ).show()
        }
    }
}
