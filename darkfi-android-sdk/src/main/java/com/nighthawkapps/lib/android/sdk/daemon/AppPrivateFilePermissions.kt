package com.nighthawkapps.lib.android.sdk.daemon

import java.io.File

/**
 * Restricts app-private files to the app UID (best-effort on Android's Java file API).
 */
internal object AppPrivateFilePermissions {
    fun restrictToAppUid(file: File) {
        file.parentFile?.let(::restrictDirectoryToAppUid)
        file.setReadable(false, false)
        file.setWritable(false, false)
        file.setExecutable(false, false)
        file.setReadable(true, true)
        file.setWritable(true, true)
    }

    fun restrictDirectoryToAppUid(dir: File) {
        if (!dir.exists()) return
        dir.setReadable(false, false)
        dir.setWritable(false, false)
        dir.setExecutable(false, false)
        dir.setReadable(true, true)
        dir.setWritable(true, true)
        dir.setExecutable(true, true)
    }
}
