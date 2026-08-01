package com.nighthawkapps.lib.android.ui.common

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SENSITIVE_CLIPBOARD_CLEAR_MS = 60_000L

private object SensitiveClipboardRegistry {
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private var clearJob: Job? = null
    private var lastSensitiveText: String? = null
    private var observerRegistered = false

    fun registerClearOnBackground(context: Context) {
        if (observerRegistered) return
        observerRegistered = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    clearIfStillPresent(context.applicationContext)
                }
            },
        )
    }

    fun scheduleTimedClear(
        context: Context,
        text: String,
    ) {
        registerClearOnBackground(context)
        lastSensitiveText = text
        clearJob?.cancel()
        clearJob =
            scope.launch {
                delay(SENSITIVE_CLIPBOARD_CLEAR_MS)
                clearIfStillPresent(context.applicationContext)
            }
    }

    private fun clearIfStillPresent(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val current =
            clipboard.primaryClip
                ?.getItemAt(0)
                ?.text
                ?.toString()
        if (current != null && current == lastSensitiveText) {
            clipboard.clearPrimaryClip()
        }
        lastSensitiveText = null
    }
}

suspend fun Clipboard.setSensitivePlainText(
    context: Context,
    text: String,
    label: String = "",
) {
    val clipData = ClipData.newPlainText(label, text)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clipData.description.extras =
            PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
    }
    setClipEntry(ClipEntry(clipData))
    SensitiveClipboardRegistry.scheduleTimedClear(context, text)
}
