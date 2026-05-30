package com.nighthawkapps.lib.android.ui.screen.backup.ext

import androidx.compose.runtime.saveable.mapSaver
import com.nighthawkapps.lib.android.spackle.model.Index
import com.nighthawkapps.lib.android.ui.screen.backup.state.TestChoices

private const val KEY_TEST_CHOICES = "test_choices" // $NON-NLS

// Using a custom saver instead of Parcelize, to avoid adding an Android-specific API to
// the TestChoices and Index class
val TestChoices.Companion.Saver
    get() =
        run {
            mapSaver(
                save = { it.toSaverMap() },
                restore = { TestChoices(fromSaverMap(it)) }
            )
        }

private fun TestChoices.toSaverMap() =
    buildMap {
        put(KEY_TEST_CHOICES, current.value.mapKeys { it.key.value })
    }

@Suppress("UNCHECKED_CAST")
private fun fromSaverMap(map: Map<String, Any?>): Map<Index, String?> =
    if (map.isEmpty()) {
        emptyMap()
    } else {
        (map[KEY_TEST_CHOICES] as Map<Int, String?>).mapKeys { Index(it.key) }
    }
