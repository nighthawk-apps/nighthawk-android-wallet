package com.nighthawkapps.lib.android.ui.screen.restore.state

import com.nighthawkapps.lib.android.sdk.wallet.DarkfiSeedPhrase
import com.nighthawkapps.lib.android.ui.common.first
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class WordList(
    initial: List<String> = emptyList()
) {
    private val mutableState: MutableStateFlow<ImmutableList<String>> =
        MutableStateFlow(initial.toPersistentList())

    val current: StateFlow<ImmutableList<String>> = mutableState

    fun set(list: List<String>) {
        mutableState.value = list.toPersistentList()
    }

    fun append(words: List<String>) {
        val newList =
            (current.value + words)
                .first(DarkfiSeedPhrase.WORD_COUNT)
                .toPersistentList()

        mutableState.value = newList
    }

    override fun toString() = "WordList"
}
