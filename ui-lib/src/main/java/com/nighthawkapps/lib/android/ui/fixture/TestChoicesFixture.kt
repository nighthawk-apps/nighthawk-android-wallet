package com.nighthawkapps.lib.android.ui.fixture

import com.nighthawkapps.lib.android.spackle.model.Index
import com.nighthawkapps.lib.android.ui.screen.backup.state.TestChoices

object TestChoicesFixture {
    val INITIAL_CHOICES =
        mapOf(
            Pair(Index(0), "baz"),
            Pair(Index(1), "foo")
        )

    fun new(initial: Map<Index, String?> = INITIAL_CHOICES) = TestChoices(initial)
}
