package com.nighthawkapps.lib.android.ui.screen.restore.model

import com.nighthawkapps.lib.android.sdk.wallet.DarkfiSeedPhrase
import com.nighthawkapps.lib.android.ui.common.first
import java.util.Locale

internal sealed class ParseResult {
    object Continue : ParseResult()

    data class Add(
        val words: List<String>
    ) : ParseResult() {
        override fun toString() = "Add"
    }

    data class Autocomplete(
        val suggestions: List<String>
    ) : ParseResult() {
        override fun toString() = "Autocomplete"
    }

    data class Warn(
        val suggestions: List<String>
    ) : ParseResult() {
        override fun toString() = "Warn"
    }

    companion object {
        private val TOKEN_SPLIT_REGEX = "\\s+".toRegex()

        @Suppress("ReturnCount")
        fun new(
            completeWordList: Set<String>,
            rawInput: String
        ): ParseResult {
            val trimmed = rawInput.lowercase(Locale.US).trim()

            if (trimmed.isBlank()) {
                return Continue
            }

            val autocomplete = completeWordList.filter { it.startsWith(trimmed) }

            if (completeWordList.contains(trimmed) && autocomplete.size == 1) {
                return Add(listOf(trimmed))
            }

            if (autocomplete.isNotEmpty()) {
                return Autocomplete(autocomplete)
            }

            val multiple =
                trimmed
                    .split(TOKEN_SPLIT_REGEX)
                    .filter { it.isNotBlank() && completeWordList.contains(it) }
                    .first(DarkfiSeedPhrase.WORD_COUNT)
            if (multiple.isNotEmpty()) {
                return Add(multiple)
            }

            return Warn(findSuggestions(trimmed, completeWordList))
        }
    }
}

internal fun findSuggestions(
    input: String,
    completeWordList: Set<String>
): List<String> =
    if (input.isBlank()) {
        emptyList()
    } else {
        completeWordList.filter { it.startsWith(input) }.ifEmpty {
            findSuggestions(input.dropLast(1), completeWordList)
        }
    }
