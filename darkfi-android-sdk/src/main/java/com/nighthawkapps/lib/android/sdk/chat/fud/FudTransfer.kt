package com.nighthawkapps.lib.android.sdk.chat.fud

import java.io.File
import java.util.Locale

/**
 * Parsed `fud://<infohash>/<filename>` offer. Geode bytes are not fetched here.
 */
data class FudUri(
    val infoHash: String,
    val fileName: String?,
    val raw: String,
) {
    val displayName: String get() = fileName ?: infoHash

    companion object {
        fun parse(raw: String): FudUri? {
            val trimmed = raw.trim()
            if (!trimmed.startsWith("fud://", ignoreCase = true)) return null
            val rest = trimmed.substring(6)
            val slash = rest.indexOf('/')
            val host = if (slash < 0) rest else rest.substring(0, slash)
            val path = if (slash < 0) "" else rest.substring(slash + 1)
            if (!isValidInfoHash(host)) return null
            val name =
                if (path.isEmpty()) {
                    null
                } else {
                    sanitizeFileName(path) ?: return null
                }
            val canonical =
                if (name == null) {
                    "fud://${host.lowercase(Locale.US)}"
                } else {
                    "fud://${host.lowercase(Locale.US)}/$name"
                }
            return FudUri(host.lowercase(Locale.US), name, canonical)
        }

        internal fun isValidInfoHash(host: String): Boolean {
            if (host.length !in 4..128) return false
            return host.all { it.isLetterOrDigit() }
        }

        internal fun sanitizeFileName(path: String): String? {
            if (path.contains('/') || path.contains('\\') || path.contains("..")) return null
            val name = path.substringAfterLast('/')
            if (name.isEmpty() || name == "." || name == ".." || name.length > 255) return null
            return name
        }
    }
}

enum class FudTransferDecision {
    Allowed,
    Disabled,
    NeedsPrivateTransport,
    Invalid,
}

/** File offers only proceed over Tor or mesh. Clearnet is rejected. */
object FudTransferPolicy {
    fun decide(
        enabled: Boolean,
        torReady: Boolean,
        meshOn: Boolean,
        uri: FudUri?,
    ): FudTransferDecision {
        if (uri == null) return FudTransferDecision.Invalid
        if (!enabled) return FudTransferDecision.Disabled
        return if (torReady || meshOn) {
            FudTransferDecision.Allowed
        } else {
            FudTransferDecision.NeedsPrivateTransport
        }
    }
}

data class QueuedFudOffer(
    val infoHash: String,
    val fileName: String?,
    val queuedAtEpochMs: Long,
)

object FudOfferInbox {
    fun queue(
        root: File,
        uri: FudUri,
        nowMs: Long = System.currentTimeMillis(),
    ): File {
        val dir = File(root, "fud-offers")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "${uri.infoHash}.json")
        val json =
            """{"infoHash":${jsonString(uri.infoHash)},"fileName":${uri.fileName?.let { jsonString(it) } ?: "null"},"queuedAtEpochMs":$nowMs}"""
        file.writeText(json)
        return file
    }

    private fun jsonString(value: String): String =
        buildString {
            append('"')
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    else -> append(ch)
                }
            }
            append('"')
        }
}
