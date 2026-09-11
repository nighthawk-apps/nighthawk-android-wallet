package com.nighthawkapps.lib.android.sdk.chat.hud

import com.nighthawkapps.lib.android.sdk.chat.ChatChannelMessage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed class ChatTimelineItem {
    data class DateSeparator(
        val epochDay: Long,
        val label: String,
    ) : ChatTimelineItem()

    data class Message(
        val message: ChatChannelMessage,
    ) : ChatTimelineItem()
}

sealed class ChatInlineSpan {
    data class Text(
        val value: String,
    ) : ChatInlineSpan()

    data class Url(
        val url: String,
    ) : ChatInlineSpan()

    data class Fud(
        val uri: String,
    ) : ChatInlineSpan()
}

object ChatTimeline {
    private val DATE_LABEL: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)

    fun group(
        messages: List<ChatChannelMessage>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ChatTimelineItem> {
        if (messages.isEmpty()) {
            return emptyList()
        }
        val items = ArrayList<ChatTimelineItem>(messages.size + 4)
        var lastDay: LocalDate? = null
        for (message in messages) {
            val day =
                Instant
                    .ofEpochMilli(message.timestampMs.coerceAtLeast(0L))
                    .atZone(zone)
                    .toLocalDate()
            if (day != lastDay) {
                items +=
                    ChatTimelineItem.DateSeparator(
                        epochDay = day.toEpochDay(),
                        label = DATE_LABEL.format(day),
                    )
                lastDay = day
            }
            items += ChatTimelineItem.Message(message)
        }
        return items
    }

    fun gutterTime(
        timestampMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val time =
            Instant
                .ofEpochMilli(timestampMs.coerceAtLeast(0L))
                .atZone(zone)
                .toLocalTime()
        return String.format(Locale.US, "%02d:%02d", time.hour, time.minute)
    }
}

object ChatMessageLexer {
    private val TOKEN =
        Regex("""(fud://[^\s]+)|(https?://[^\s<>"]+)""", RegexOption.IGNORE_CASE)

    fun lex(text: String): List<ChatInlineSpan> {
        if (text.isEmpty()) {
            return emptyList()
        }
        val spans = ArrayList<ChatInlineSpan>()
        var cursor = 0
        for (match in TOKEN.findAll(text)) {
            if (match.range.first > cursor) {
                spans += ChatInlineSpan.Text(text.substring(cursor, match.range.first))
            }
            val fud = match.groups[1]?.value
            val url = match.groups[2]?.value
            when {
                fud != null -> spans += ChatInlineSpan.Fud(trimTrailingPunctuation(fud))
                url != null -> spans += ChatInlineSpan.Url(trimTrailingPunctuation(url))
            }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) {
            spans += ChatInlineSpan.Text(text.substring(cursor))
        }
        return spans
    }

    fun hasFud(text: String): Boolean = lex(text).any { it is ChatInlineSpan.Fud }

    private fun trimTrailingPunctuation(value: String): String =
        value.trimEnd { it == '.' || it == ',' || it == ';' || it == ')' }
}
