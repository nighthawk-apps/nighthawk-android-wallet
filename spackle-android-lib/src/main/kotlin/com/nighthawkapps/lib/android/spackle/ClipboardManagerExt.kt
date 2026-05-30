@file:Suppress("ktlint:standard:filename")

package com.nighthawkapps.lib.android.spackle

import android.content.ClipData
import android.content.ClipboardManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun ClipboardManager.setPrimaryClipSuspend(data: ClipData) =
    withContext(Dispatchers.IO) {
        setPrimaryClip(data)
    }
