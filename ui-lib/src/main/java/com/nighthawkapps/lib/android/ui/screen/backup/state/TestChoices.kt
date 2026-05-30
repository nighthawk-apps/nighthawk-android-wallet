package com.nighthawkapps.lib.android.ui.screen.backup.state

import com.nighthawkapps.lib.android.spackle.model.Index
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TestChoices(
    initial: Map<Index, String?> = emptyMap()
) {
    private val mutableState = MutableStateFlow<ImmutableMap<Index, String?>>(initial.toPersistentMap())

    val current: StateFlow<ImmutableMap<Index, String?>> = mutableState

    fun set(map: Map<Index, String?>) {
        mutableState.value = map.toPersistentMap()
    }

    companion object
}
