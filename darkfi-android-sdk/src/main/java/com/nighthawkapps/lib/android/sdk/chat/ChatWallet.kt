package com.nighthawkapps.lib.android.sdk.chat

import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
import kotlin.math.abs

/** Delivery of an outgoing chat line. */
enum class ChatDeliveryState {
    Queued,
    Sending,
    Relayed,
    Failed,
}

/** On-chain receipt posted into a thread. */
enum class ChatReceiptPhase {
    Pending,
    Broadcasted,
    Confirmed,
    Failed,
}

data class ChatInvoice(
    val uri: String,
    val address: String,
    val amount: String?,
    val memo: String?,
    val token: String?,
)

data class ChatReceipt(
    val id: String,
    val phase: ChatReceiptPhase,
    val amount: String,
    val address: String,
    val memo: String?,
    val txId: String?,
)

/**
 * One person: chat nickname, their DM public key, and an optional DRK address.
 * The DM secret stays in the existing keystore entry keyed by the same nickname.
 */
data class NighthawkContact(
    val id: String,
    val nickname: String,
    val dmPublicKey: String,
    val walletAddress: String?,
)

object ChatInvoices {
    private val TOKEN =
        Regex("""(?i)(?:drk|darkfi):[^\s<>"']+""")

    fun find(text: String): ChatInvoice? {
        val match = TOKEN.find(text) ?: return null
        val raw = match.value.trimEnd { it == '.' || it == ',' || it == ';' || it == ')' }
        return parse(raw)
    }

    fun parse(raw: String): ChatInvoice? {
        val input = raw.trim()
        val lower = input.lowercase(Locale.ROOT)
        val schemeLen =
            when {
                lower.startsWith("drk:") -> 4
                lower.startsWith("darkfi:") -> 7
                else -> return null
            }
        var rest = input.substring(schemeLen)
        if (rest.startsWith("//")) rest = rest.substring(2)
        val q = rest.indexOf('?')
        val address = (if (q >= 0) rest.substring(0, q) else rest).trim()
        if (address.isEmpty() || address.length > 256) return null
        if (address.any { it.isWhitespace() || it.isISOControl() }) return null
        var amount: String? = null
        var memo: String? = null
        var token: String? = null
        if (q >= 0) {
            for (pair in rest.substring(q + 1).split('&')) {
                if (pair.isEmpty()) continue
                val eq = pair.indexOf('=')
                val key = (if (eq >= 0) pair.substring(0, eq) else pair).lowercase(Locale.ROOT)
                val value = urlDecode(if (eq >= 0) pair.substring(eq + 1) else "")
                when (key) {
                    "amount" -> if (value.isNotBlank()) amount = value
                    "token" -> if (value.isNotBlank()) token = value
                    "memo" -> memo = decodeMemo(value)
                }
            }
        }
        return ChatInvoice(
            uri = input,
            address = address,
            amount = amount,
            memo = memo,
            token = token,
        )
    }

    private fun decodeMemo(value: String): String? {
        if (value.isBlank()) return null
        val b64 =
            runCatching {
                String(Base64.getDecoder().decode(value), Charsets.UTF_8)
            }.getOrNull()
        val text = b64?.takeIf { it.isNotBlank() } ?: value
        if (text.any(Char::isISOControl)) return null
        return text
    }

    private fun urlDecode(value: String): String =
        runCatching { java.net.URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrDefault(value)
}

object ChatReceiptCodec {
    const val PREFIX = "nh-receipt:v1"

    fun encode(receipt: ChatReceipt): String {
        val memo =
            receipt.memo
                ?.takeIf { it.isNotBlank() }
                ?.let { Base64.getEncoder().encodeToString(it.toByteArray(Charsets.UTF_8)) }
                ?: "-"
        val tx = receipt.txId?.takeIf { it.isNotBlank() } ?: "-"
        return listOf(
            PREFIX,
            receipt.phase.name.lowercase(Locale.ROOT),
            sanitize(receipt.id),
            sanitize(receipt.amount),
            sanitize(receipt.address),
            memo,
            sanitize(tx),
        ).joinToString("|")
    }

    fun parse(text: String): ChatReceipt? {
        val trimmed = text.trim()
        if (!trimmed.startsWith("$PREFIX|")) return null
        val parts = trimmed.split('|')
        if (parts.size < 7) return null
        val phase =
            when (parts[1].lowercase(Locale.ROOT)) {
                "pending" -> ChatReceiptPhase.Pending
                "broadcasted" -> ChatReceiptPhase.Broadcasted
                "confirmed" -> ChatReceiptPhase.Confirmed
                "failed" -> ChatReceiptPhase.Failed
                else -> return null
            }
        val memo =
            if (parts[5] == "-") {
                null
            } else {
                runCatching {
                    String(Base64.getDecoder().decode(parts[5]), Charsets.UTF_8)
                }.getOrNull()
            }
        val tx = parts[6].takeIf { it.isNotBlank() && it != "-" }
        return ChatReceipt(
            id = parts[2],
            phase = phase,
            amount = parts[3],
            address = parts[4],
            memo = memo,
            txId = tx,
        )
    }

    private fun sanitize(value: String): String = value.replace("|", "").trim()
}

object ChatNotificationPolicy {
    const val LOCKSCREEN_TEXT = "New private message"
    const val EXTRA_THREAD = "nighthawk_chat_thread"
    const val FRESH_MS = 90_000L

    fun shouldAlert(
        isOutgoing: Boolean,
        ownNick: String,
        nick: String,
        timestampMs: Long,
        nowMs: Long,
    ): Boolean {
        if (isOutgoing) return false
        if (ownNick.isNotBlank() && nick.equals(ownNick, ignoreCase = true)) return false
        if (nick.equals("System", ignoreCase = true)) return false
        if (timestampMs > 0L && nowMs - timestampMs > FRESH_MS) return false
        if (timestampMs > nowMs + 5_000L) return false
        return true
    }

    fun deepLink(thread: String): String =
        "nighthawk://chat?thread=" + java.net.URLEncoder.encode(thread, Charsets.UTF_8.name())
}

object ChatLinkGuard {
    fun isClearnetHttp(url: String): Boolean {
        val lower = url.trim().lowercase(Locale.ROOT)
        return lower.startsWith("http://") || lower.startsWith("https://")
    }
}

object ChatDeliveryLabel {
    fun from(
        eventId: String,
        isOutgoing: Boolean,
    ): ChatDeliveryState? =
        when {
            eventId.startsWith("failed-") -> ChatDeliveryState.Failed
            eventId.startsWith("queued-") -> ChatDeliveryState.Queued
            eventId.startsWith("local-") -> ChatDeliveryState.Sending
            isOutgoing -> ChatDeliveryState.Relayed
            else -> null
        }
}

object NighthawkContacts {
    fun safetyNumber(
        dmPublicKey: String,
        walletAddress: String?,
    ): String {
        val material = dmPublicKey.trim() + "|" + (walletAddress?.trim().orEmpty())
        val digest = MessageDigest.getInstance("SHA-256").digest(material.toByteArray(Charsets.UTF_8))
        return digest.take(15).joinToString(" ") { byte ->
            "%05d".format(Locale.US, abs(byte.toInt()) % 100_000)
        }
    }

    /**
     * Wallet payments use a DarkFi standard address inside a `drk:` invoice.
     * DAO identity is a DarkFi bulla, not a separate address scheme.
     */
    fun inviteUri(contact: NighthawkContact): String? {
        val addr = contact.walletAddress?.trim().orEmpty()
        if (addr.isEmpty()) return null
        return "drk:$addr"
    }

    fun parseInvite(raw: String): NighthawkContact? {
        val invoice = ChatInvoices.parse(raw.trim()) ?: return null
        return NighthawkContact(
            id = invoice.address.lowercase(Locale.ROOT),
            nickname = "",
            dmPublicKey = "",
            walletAddress = invoice.address,
        )
    }
}
